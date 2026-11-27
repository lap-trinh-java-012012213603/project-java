package com.analoghub.platform.aiassistant.service.cv;

import com.analoghub.platform.aiassistant.client.ClassicalCvMetrics;
import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Stage-1 Classical Computer Vision Analyzer running 100% on CPU (< 25ms / frame):
 * 1. Gaussian 3x3 Film-Grain Denoising (sigma=1.0) + Laplacian Variance (Sharpness)
 * 2. YCbCr Luminance Mean & Dark/Bright Clipping Ratios (Exposure)
 * 3. Morphological White Top-Hat & Black-Hat + Connected Components (Dust & Scratches)
 * 4. CIELAB Chroma Deviation (Color Cast) & Asymmetric Border Gradient (Light Leak)
 * 5. Unified Stage-1 Overall Quality Score (0..100)
 * (Sections 3 & 4 of 04-scan-quality-cv-pipeline.md).
 */
@Slf4j
@Service
public class ClassicalCvAnalyzer {

    public static final int MAX_WORKING_DIMENSION = 384;

    // Section 3.1 Sharpness thresholds
    public static final double SHARPNESS_BLURRY_THRESHOLD = 80.0;
    public static final double SHARPNESS_VERY_SHARP_THRESHOLD = 120.0;

    // Section 3.2 Exposure thresholds
    public static final double LUMINANCE_UNDEREXPOSED_THRESHOLD = 50.0;
    public static final double LUMINANCE_OVEREXPOSED_THRESHOLD = 200.0;
    public static final double DARK_PIXEL_CLIP_THRESHOLD = 10.0;
    public static final double BRIGHT_PIXEL_CLIP_THRESHOLD = 245.0;
    public static final double DARK_RATIO_UNDEREXPOSED_THRESHOLD = 0.40;
    public static final double BRIGHT_RATIO_OVEREXPOSED_THRESHOLD = 0.35;

    // Section 3.3 Dust & Scratch thresholds
    public static final int DUST_SCRATCH_DEFECT_THRESHOLD = 40;
    public static final int MORPH_INTENSITY_DELTA_THRESHOLD = 28;

    // Section 3.4 CIELAB Color Cast & Light Leak thresholds
    public static final double COLOR_CAST_DEFECT_THRESHOLD = 18.0;
    public static final double LIGHT_LEAK_EDGE_TO_CENTER_RATIO = 2.2;
    public static final double LIGHT_LEAK_CIELAB_A_STAR_THRESHOLD = 25.0;
    public static final double STAGE2_ESCALATION_SCORE_THRESHOLD = 75.0;

    public static final String DEFECT_GOOD = "GOOD";
    public static final String DEFECT_BLURRY = "BLURRY";
    public static final String DEFECT_UNDEREXPOSED = "UNDEREXPOSED";
    public static final String DEFECT_OVEREXPOSED = "OVEREXPOSED";
    public static final String DEFECT_DUST_SCRATCH = "DUST_SCRATCH";
    public static final String DEFECT_LIGHT_LEAK = "LIGHT_LEAK";
    public static final String DEFECT_COLOR_CAST = "COLOR_CAST";

    @Builder
    public record AnomalyBlob(
            String type, // "DUST" or "SCRATCH"
            int centroidX,
            int centroidY,
            double normalizedY,
            int areaPx,
            int widthPx,
            int heightPx
    ) {
    }

    @Builder
    public record Stage1AnalysisResult(
            ClassicalCvMetrics metrics,
            double overallQualityScore,
            String primaryDefect,
            boolean lightLeakDetected,
            boolean colorCastDetected,
            boolean requiresStage2VisionLlm,
            int dustSpeckCount,
            int scratchLineCount,
            List<AnomalyBlob> detectedAnomalies,
            long executionTimeMs
    ) {
    }

    public Stage1AnalysisResult analyzeImageBytes(byte[] imageBytes, boolean isBlackAndWhiteFilm) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException(ErrorCode.IMAGE_QC_ANALYSIS_FAILED, "Dữ liệu byte của bức ảnh scan rỗng.");
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                throw new BusinessException(
                        ErrorCode.IMAGE_QC_ANALYSIS_FAILED,
                        "Không thể giải mã định dạng ảnh scan để phân tích chất lượng Stage-1."
                );
            }
            return analyzeBufferedImage(image, isBlackAndWhiteFilm);
        } catch (IOException ex) {
            throw new BusinessException(
                    ErrorCode.IMAGE_QC_ANALYSIS_FAILED,
                    "Lỗi đọc dữ liệu điểm ảnh phục vụ kiểm định chất lượng (Scan QC): " + ex.getMessage(),
                    ex
            );
        }
    }

    public Stage1AnalysisResult analyzeBufferedImage(BufferedImage sourceImage, boolean isBlackAndWhiteFilm) {
        long startNanos = System.nanoTime();
        if (sourceImage == null || sourceImage.getWidth() < 8 || sourceImage.getHeight() < 8) {
            throw new BusinessException(ErrorCode.IMAGE_QC_ANALYSIS_FAILED, "Kích thước ảnh scan không hợp lệ.");
        }

        BufferedImage workingImg = downsampleIfNeeded(sourceImage, MAX_WORKING_DIMENSION);
        int width = workingImg.getWidth();
        int height = workingImg.getHeight();
        int totalPixels = width * height;

        int[] argbPixels = workingImg.getRGB(0, 0, width, height, null, 0, width);
        double[][] luminanceY = new double[height][width];

        // 1. Exposure & CIELAB / Border Light-Leak accumulators
        double sumY = 0.0;
        int darkCount = 0;
        int brightCount = 0;

        double sumMidAStar = 0.0;
        double sumMidBStar = 0.0;
        int midToneCount = 0;

        int borderMarginX = Math.max(1, (int) Math.round(width * 0.08));
        int borderMarginY = Math.max(1, (int) Math.round(height * 0.08));

        double sumCenterY = 0.0;
        int countCenter = 0;

        // 4 outer border strips: 0=LEFT, 1=RIGHT, 2=TOP, 3=BOTTOM
        double[] stripSumY = new double[4];
        double[] stripSumAStar = new double[4];
        int[] stripCount = new int[4];

        for (int y = 0; y < height; y++) {
            double[] rowY = luminanceY[y];
            for (int x = 0; x < width; x++) {
                int rgb = argbPixels[y * width + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // BT.601 YCbCr Luminance Y in [0, 255]
                double yVal = (0.299 * r) + (0.587 * g) + (0.114 * b);
                rowY[x] = yVal;
                sumY += yVal;

                if (yVal < DARK_PIXEL_CLIP_THRESHOLD) {
                    darkCount++;
                } else if (yVal > BRIGHT_PIXEL_CLIP_THRESHOLD) {
                    brightCount++;
                }

                double[] lab = rgbToCieLab(r, g, b);
                double aStar = lab[1];
                double bStar = lab[2];

                if (yVal > 20.0 && yVal < 235.0) {
                    sumMidAStar += aStar;
                    sumMidBStar += bStar;
                    midToneCount++;
                }

                boolean inLeft = x < borderMarginX;
                boolean inRight = x >= width - borderMarginX;
                boolean inTop = y < borderMarginY;
                boolean inBottom = y >= height - borderMarginY;

                double effectiveEdgeBrightness = Math.max(yVal, (0.65 * r) + (0.35 * g));

                if (inLeft) {
                    stripSumY[0] += effectiveEdgeBrightness;
                    stripSumAStar[0] += aStar;
                    stripCount[0]++;
                }
                if (inRight) {
                    stripSumY[1] += effectiveEdgeBrightness;
                    stripSumAStar[1] += aStar;
                    stripCount[1]++;
                }
                if (inTop) {
                    stripSumY[2] += effectiveEdgeBrightness;
                    stripSumAStar[2] += aStar;
                    stripCount[2]++;
                }
                if (inBottom) {
                    stripSumY[3] += effectiveEdgeBrightness;
                    stripSumAStar[3] += aStar;
                    stripCount[3]++;
                }
                if (!inLeft && !inRight && !inTop && !inBottom) {
                    sumCenterY += yVal;
                    countCenter++;
                }
            }
        }

        double meanLuminance = sumY / totalPixels;
        double darkRatio = (double) darkCount / totalPixels;
        double brightRatio = (double) brightCount / totalPixels;

        // Section 3.4.1: CIELAB Color Cast C* = sqrt((mean_a*)^2 + (mean_b*)^2)
        double colorCastCieScore = 0.0;
        if (!isBlackAndWhiteFilm && midToneCount > 0) {
            double meanA = sumMidAStar / midToneCount;
            double meanB = sumMidBStar / midToneCount;
            colorCastCieScore = Math.sqrt((meanA * meanA) + (meanB * meanB));
        }

        // Section 3.4.2: Asymmetric Border Light Leak Detection
        double centerMeanY = countCenter > 0 ? (sumCenterY / countCenter) : meanLuminance;
        boolean lightLeakDetected = false;
        for (int s = 0; s < 4; s++) {
            if (stripCount[s] > 0) {
                double edgeMeanY = stripSumY[s] / stripCount[s];
                double edgeMeanAStar = stripSumAStar[s] / stripCount[s];
                if (edgeMeanY > LIGHT_LEAK_EDGE_TO_CENTER_RATIO * Math.max(10.0, centerMeanY)
                        && edgeMeanAStar > LIGHT_LEAK_CIELAB_A_STAR_THRESHOLD) {
                    lightLeakDetected = true;
                    break;
                }
            }
        }

        // Section 3.1: Gaussian 3x3 Denoising (sigma = 1.0) + Laplacian Variance Sharpness
        double[][] gaussianSmoothed = applyGaussian3x3(luminanceY, width, height);
        double laplacianSharpnessScore = computeLaplacianVariance(gaussianSmoothed, width, height);

        // Section 3.3: Morphological White Top-Hat & Black-Hat + Connected Components
        MorphologyDetectionResult morphResult = detectDustAndScratchesMorphological(luminanceY, width, height);
        int totalBlobs = morphResult.dustCount + morphResult.scratchCount;

        // Section 4: Unified Overall Quality Score (0..100)
        double overallScore = computeOverallQualityScore(
                laplacianSharpnessScore,
                meanLuminance,
                darkRatio,
                brightRatio,
                totalBlobs,
                colorCastCieScore,
                isBlackAndWhiteFilm,
                lightLeakDetected
        );

        boolean colorCastDetected = !isBlackAndWhiteFilm && colorCastCieScore > COLOR_CAST_DEFECT_THRESHOLD;
        String primaryDefect = classifyPrimaryDefect(
                laplacianSharpnessScore,
                meanLuminance,
                darkRatio,
                brightRatio,
                totalBlobs,
                lightLeakDetected,
                colorCastDetected
        );

        boolean requiresStage2 = overallScore < STAGE2_ESCALATION_SCORE_THRESHOLD
                || !DEFECT_GOOD.equals(primaryDefect);

        ClassicalCvMetrics metrics = ClassicalCvMetrics.builder()
                .laplacianSharpnessScore(round2(laplacianSharpnessScore))
                .exposureMeanLuminance(round2(meanLuminance))
                .darkPixelRatio(round3(darkRatio))
                .brightPixelRatio(round3(brightRatio))
                .dustScratchBlobCount(totalBlobs)
                .colorCastCieScore(round2(colorCastCieScore))
                .isBlackAndWhiteFilm(isBlackAndWhiteFilm)
                .build();

        long executionTimeMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        return Stage1AnalysisResult.builder()
                .metrics(metrics)
                .overallQualityScore(round2(overallScore))
                .primaryDefect(primaryDefect)
                .lightLeakDetected(lightLeakDetected)
                .colorCastDetected(colorCastDetected)
                .requiresStage2VisionLlm(requiresStage2)
                .dustSpeckCount(morphResult.dustCount)
                .scratchLineCount(morphResult.scratchCount)
                .detectedAnomalies(morphResult.anomalies)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    /**
     * Section 4: Overall Quality Score formula (0..100):
     * overall_score = 100 - (P_sharpness + P_exposure + P_dust + P_color + P_leak)
     */
    public static double computeOverallQualityScore(
            double sharpnessScore,
            double meanLuminance,
            double darkRatio,
            double brightRatio,
            int dustBlobCount,
            double colorCastCieScore,
            boolean isBlackAndWhiteFilm,
            boolean lightLeakDetected
    ) {
        double pSharpness = Math.min(35.0, Math.max(0.0, 100.0 - sharpnessScore) * 0.35);
        double pExposure = (Math.abs(meanLuminance - 128.0) * 0.25)
                + (darkRatio * 20.0)
                + (brightRatio * 20.0);
        double pDust = Math.min(30.0, Math.max(0, dustBlobCount) * 0.6);
        double pColor = isBlackAndWhiteFilm
                ? 0.0
                : Math.min(20.0, Math.max(0.0, colorCastCieScore - 10.0) * 1.5);
        double pLeak = lightLeakDetected ? 40.0 : 0.0;

        double rawScore = 100.0 - (pSharpness + pExposure + pDust + pColor + pLeak);
        return Math.max(0.0, Math.min(100.0, rawScore));
    }

    /**
     * Section 3.1: 3x3 Gaussian Filter (sigma = 1.0) to suppress film grain before Laplacian.
     */
    static double[][] applyGaussian3x3(double[][] src, int width, int height) {
        double[][] dst = new double[height][width];
        for (int y = 0; y < height; y++) {
            int y0 = Math.max(0, y - 1);
            int y2 = Math.min(height - 1, y + 1);
            for (int x = 0; x < width; x++) {
                int x0 = Math.max(0, x - 1);
                int x2 = Math.min(width - 1, x + 1);

                double sum = (src[y0][x0] + 2.0 * src[y0][x] + src[y0][x2])
                        + (2.0 * src[y][x0] + 4.0 * src[y][x] + 2.0 * src[y][x2])
                        + (src[y2][x0] + 2.0 * src[y2][x] + src[y2][x2]);
                dst[y][x] = sum / 16.0;
            }
        }
        return dst;
    }

    /**
     * Section 3.1: Discrete Laplacian convolution L(x, y) and variance sigma^2(L).
     */
    static double computeLaplacianVariance(double[][] smoothed, int width, int height) {
        if (width < 3 || height < 3) {
            return 0.0;
        }
        int innerWidth = width - 2;
        int innerHeight = height - 2;
        int count = innerWidth * innerHeight;
        double[] lapValues = new double[count];

        double sum = 0.0;
        int idx = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double lap = smoothed[y - 1][x]
                        + smoothed[y + 1][x]
                        + smoothed[y][x - 1]
                        + smoothed[y][x + 1]
                        - (4.0 * smoothed[y][x]);
                lapValues[idx++] = lap;
                sum += lap;
            }
        }

        double mean = sum / count;
        double sumSqDiff = 0.0;
        for (int i = 0; i < count; i++) {
            double diff = lapValues[i] - mean;
            sumSqDiff += diff * diff;
        }
        return sumSqDiff / count;
    }

    private record MorphologyDetectionResult(
            int dustCount,
            int scratchCount,
            List<AnomalyBlob> anomalies
    ) {
    }

    /**
     * Section 3.3: White Top-Hat WTH(I) = I - (I o B) and Black-Hat BTH(I) = (I . B) - I
     * with 5x5 structuring neighborhood + Connected Components labeling.
     */
    static MorphologyDetectionResult detectDustAndScratchesMorphological(
            double[][] luminance,
            int width,
            int height
    ) {
        double[][] eroded = morphErode5x5(luminance, width, height);
        double[][] opened = morphDilate5x5(eroded, width, height);

        double[][] dilated = morphDilate5x5(luminance, width, height);
        double[][] closed = morphErode5x5(dilated, width, height);

        boolean[][] anomalyMask = new boolean[height][width];
        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                double wth = luminance[y][x] - opened[y][x];
                double bth = closed[y][x] - luminance[y][x];
                if (wth >= MORPH_INTENSITY_DELTA_THRESHOLD || bth >= MORPH_INTENSITY_DELTA_THRESHOLD) {
                    anomalyMask[y][x] = true;
                }
            }
        }

        boolean[][] visited = new boolean[height][width];
        List<AnomalyBlob> anomalies = new ArrayList<>();
        int dustCount = 0;
        int scratchCount = 0;

        int[] dx = {-1, 0, 1, -1, 1, -1, 0, 1};
        int[] dy = {-1, -1, -1, 0, 0, 1, 1, 1};
        Queue<int[]> queue = new ArrayDeque<>();

        for (int y = 2; y < height - 2; y++) {
            for (int x = 2; x < width - 2; x++) {
                if (!anomalyMask[y][x] || visited[y][x]) {
                    continue;
                }

                visited[y][x] = true;
                queue.clear();
                queue.add(new int[]{x, y});

                int area = 0;
                int minX = x;
                int maxX = x;
                int minY = y;
                int maxY = y;
                long sumX = 0;
                long sumY = 0;

                while (!queue.isEmpty()) {
                    int[] curr = queue.poll();
                    int cx = curr[0];
                    int cy = curr[1];
                    area++;
                    sumX += cx;
                    sumY += cy;

                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;

                    for (int d = 0; d < 8; d++) {
                        int nx = cx + dx[d];
                        int ny = cy + dy[d];
                        if (nx >= 2 && nx < width - 2 && ny >= 2 && ny < height - 2
                                && anomalyMask[ny][nx] && !visited[ny][nx]) {
                            visited[ny][nx] = true;
                            queue.add(new int[]{nx, ny});
                        }
                    }
                }

                int bboxW = (maxX - minX) + 1;
                int bboxH = (maxY - minY) + 1;
                int maxDim = Math.max(bboxW, bboxH);
                int minDim = Math.max(1, Math.min(bboxW, bboxH));
                double aspectRatio = (double) maxDim / minDim;

                int centroidX = (int) (sumX / area);
                int centroidY = (int) (sumY / area);
                double normY = (double) centroidY / height;

                // Section 3.3.3 Classification rules:
                // Elongated scratch: aspect ratio >= 4.0 and length >= 20px
                if (aspectRatio >= 4.0 && maxDim >= 20) {
                    scratchCount++;
                    anomalies.add(AnomalyBlob.builder()
                            .type("SCRATCH")
                            .centroidX(centroidX)
                            .centroidY(centroidY)
                            .normalizedY(round3(normY))
                            .areaPx(area)
                            .widthPx(bboxW)
                            .heightPx(bboxH)
                            .build());
                } else if (area >= 2 && area <= 80) {
                    dustCount++;
                    anomalies.add(AnomalyBlob.builder()
                            .type("DUST")
                            .centroidX(centroidX)
                            .centroidY(centroidY)
                            .normalizedY(round3(normY))
                            .areaPx(area)
                            .widthPx(bboxW)
                            .heightPx(bboxH)
                            .build());
                }
            }
        }

        return new MorphologyDetectionResult(dustCount, scratchCount, List.copyOf(anomalies));
    }

    private static double[][] morphErode5x5(double[][] src, int width, int height) {
        double[][] temp = new double[height][width];
        // Horizontal 1x5 min pass
        for (int y = 0; y < height; y++) {
            double[] sRow = src[y];
            double[] tRow = temp[y];
            for (int x = 0; x < width; x++) {
                double m = sRow[x];
                int xStart = Math.max(0, x - 2);
                int xEnd = Math.min(width - 1, x + 2);
                for (int kx = xStart; kx <= xEnd; kx++) {
                    if (sRow[kx] < m) {
                        m = sRow[kx];
                    }
                }
                tRow[x] = m;
            }
        }
        // Vertical 5x1 min pass
        double[][] dst = new double[height][width];
        for (int y = 0; y < height; y++) {
            int yStart = Math.max(0, y - 2);
            int yEnd = Math.min(height - 1, y + 2);
            double[] dRow = dst[y];
            for (int x = 0; x < width; x++) {
                double m = temp[y][x];
                for (int ky = yStart; ky <= yEnd; ky++) {
                    if (temp[ky][x] < m) {
                        m = temp[ky][x];
                    }
                }
                dRow[x] = m;
            }
        }
        return dst;
    }

    private static double[][] morphDilate5x5(double[][] src, int width, int height) {
        double[][] temp = new double[height][width];
        // Horizontal 1x5 max pass
        for (int y = 0; y < height; y++) {
            double[] sRow = src[y];
            double[] tRow = temp[y];
            for (int x = 0; x < width; x++) {
                double m = sRow[x];
                int xStart = Math.max(0, x - 2);
                int xEnd = Math.min(width - 1, x + 2);
                for (int kx = xStart; kx <= xEnd; kx++) {
                    if (sRow[kx] > m) {
                        m = sRow[kx];
                    }
                }
                tRow[x] = m;
            }
        }
        // Vertical 5x1 max pass
        double[][] dst = new double[height][width];
        for (int y = 0; y < height; y++) {
            int yStart = Math.max(0, y - 2);
            int yEnd = Math.min(height - 1, y + 2);
            double[] dRow = dst[y];
            for (int x = 0; x < width; x++) {
                double m = temp[y][x];
                for (int ky = yStart; ky <= yEnd; ky++) {
                    if (temp[ky][x] > m) {
                        m = temp[ky][x];
                    }
                }
                dRow[x] = m;
            }
        }
        return dst;
    }

    private static String classifyPrimaryDefect(
            double sharpness,
            double meanLuminance,
            double darkRatio,
            double brightRatio,
            int dustBlobCount,
            boolean lightLeakDetected,
            boolean colorCastDetected
    ) {
        if (lightLeakDetected) {
            return DEFECT_LIGHT_LEAK;
        }
        if (meanLuminance < LUMINANCE_UNDEREXPOSED_THRESHOLD || darkRatio > DARK_RATIO_UNDEREXPOSED_THRESHOLD) {
            return DEFECT_UNDEREXPOSED;
        }
        if (meanLuminance > LUMINANCE_OVEREXPOSED_THRESHOLD || brightRatio > BRIGHT_RATIO_OVEREXPOSED_THRESHOLD) {
            return DEFECT_OVEREXPOSED;
        }
        if (dustBlobCount > DUST_SCRATCH_DEFECT_THRESHOLD) {
            return DEFECT_DUST_SCRATCH;
        }
        if (sharpness < SHARPNESS_BLURRY_THRESHOLD) {
            return DEFECT_BLURRY;
        }
        if (colorCastDetected) {
            return DEFECT_COLOR_CAST;
        }
        return DEFECT_GOOD;
    }

    /**
     * Converts sRGB (0..255) to CIE 1976 L*a*b* (D65 reference white).
     */
    static double[] rgbToCieLab(int r, int g, int b) {
        double sr = pivotSrgb(r / 255.0);
        double sg = pivotSrgb(g / 255.0);
        double sb = pivotSrgb(b / 255.0);

        double x = (sr * 0.4124564 + sg * 0.3575761 + sb * 0.1804375) / 0.95047;
        double y = (sr * 0.2126729 + sg * 0.7151522 + sb * 0.0721750) / 1.00000;
        double z = (sr * 0.0193339 + sg * 0.1191920 + sb * 0.9503041) / 1.08883;

        double fx = pivotXyz(x);
        double fy = pivotXyz(y);
        double fz = pivotXyz(z);

        double lStar = Math.max(0.0, 116.0 * fy - 16.0);
        double aStar = 500.0 * (fx - fy);
        double bStar = 200.0 * (fy - fz);
        return new double[]{lStar, aStar, bStar};
    }

    private static double pivotSrgb(double n) {
        return n > 0.04045 ? Math.pow((n + 0.055) / 1.055, 2.4) : (n / 12.92);
    }

    private static double pivotXyz(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    private static BufferedImage downsampleIfNeeded(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        int currentMax = Math.max(w, h);
        if (currentMax <= maxDim) {
            return src;
        }
        double scale = (double) maxDim / currentMax;
        int targetW = Math.max(8, (int) Math.round(w * scale));
        int targetH = Math.max(8, (int) Math.round(h * scale));

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g2d.dispose();
        }
        return resized;
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    private static double round3(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
