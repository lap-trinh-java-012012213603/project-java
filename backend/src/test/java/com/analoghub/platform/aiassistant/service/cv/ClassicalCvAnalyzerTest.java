package com.analoghub.platform.aiassistant.service.cv;

import com.analoghub.platform.aiassistant.service.cv.ClassicalCvAnalyzer.AnomalyBlob;
import com.analoghub.platform.aiassistant.service.cv.ClassicalCvAnalyzer.Stage1AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClassicalCvAnalyzerTest {

    private ClassicalCvAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ClassicalCvAnalyzer();
    }

    @Test
    void sharpWellExposedFramePassesStage1WithHighScore() throws IOException {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        // Create crisp 16x16 neutral checkerboard blocks around luminance 128 (values 96 and 160)
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                boolean even = ((x / 16) + (y / 16)) % 2 == 0;
                int v = even ? 160 : 96;
                int rgb = (v << 16) | (v << 8) | v;
                img.setRGB(x, y, rgb);
            }
        }

        byte[] pngBytes = toPngBytes(img);
        Stage1AnalysisResult result = analyzer.analyzeImageBytes(pngBytes, false);

        assertThat(result.metrics().laplacianSharpnessScore()).isGreaterThanOrEqualTo(120.0);
        assertThat(result.metrics().exposureMeanLuminance()).isBetween(120.0, 136.0);
        assertThat(result.lightLeakDetected()).isFalse();
        assertThat(result.colorCastDetected()).isFalse();
        assertThat(result.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_GOOD);
        assertThat(result.overallQualityScore()).isGreaterThanOrEqualTo(85.0);
        assertThat(result.requiresStage2VisionLlm()).isFalse();
    }

    @Test
    void outOfFocusBlurryImageIsDetectedAccurately() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        // Smooth gentle gradient (out-of-focus / blurry frame) with balanced mean luminance ~128
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                int v = 110 + (int) (36.0 * ((double) x / 256.0));
                int rgb = (v << 16) | (v << 8) | v;
                img.setRGB(x, y, rgb);
            }
        }

        Stage1AnalysisResult result = analyzer.analyzeBufferedImage(img, false);

        assertThat(result.metrics().laplacianSharpnessScore())
                .isLessThan(ClassicalCvAnalyzer.SHARPNESS_BLURRY_THRESHOLD);
        assertThat(result.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_BLURRY);
        assertThat(result.requiresStage2VisionLlm()).isTrue();
    }

    @Test
    void lightLeakOnFrameBorderIsDetectedAccurately() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        // Normal crisp interior around luminance 65, with bright orange/red light leak on right 8% border
        int rightEdgeStart = (int) (256 * 0.92);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                if (x >= rightEdgeStart) {
                    // Bright red-orange light leak (R=255, G=90, B=15 -> high luminance & high CIELAB a*)
                    int rgb = (255 << 16) | (90 << 8) | 15;
                    img.setRGB(x, y, rgb);
                } else {
                    int v = (((x / 8) + (y / 8)) % 2 == 0) ? 80 : 50;
                    int rgb = (v << 16) | (v << 8) | v;
                    img.setRGB(x, y, rgb);
                }
            }
        }

        Stage1AnalysisResult result = analyzer.analyzeBufferedImage(img, false);

        assertThat(result.lightLeakDetected()).isTrue();
        assertThat(result.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_LIGHT_LEAK);
        assertThat(result.overallQualityScore()).isLessThan(ClassicalCvAnalyzer.STAGE2_ESCALATION_SCORE_THRESHOLD);
        assertThat(result.requiresStage2VisionLlm()).isTrue();
    }

    @Test
    void dustSpecksAndElongatedScratchesAreDetectedViaTopHatMorphology() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setColor(new Color(120, 120, 120));
        g2d.fillRect(0, 0, 256, 256);

        // Add 10 bright dust specks (2x2 px each)
        g2d.setColor(new Color(245, 245, 245));
        for (int i = 0; i < 10; i++) {
            int dx = 20 + (i * 20);
            int dy = 40 + ((i % 3) * 25);
            g2d.fillRect(dx, dy, 2, 2);
        }

        // Add 1 elongated horizontal scanner scratch at y = 140 (length = 80px, thickness = 2px)
        g2d.fillRect(40, 140, 80, 2);
        g2d.dispose();

        Stage1AnalysisResult result = analyzer.analyzeBufferedImage(img, false);

        assertThat(result.dustSpeckCount()).isGreaterThanOrEqualTo(10);
        assertThat(result.scratchLineCount()).isGreaterThanOrEqualTo(1);
        assertThat(result.detectedAnomalies())
                .extracting(AnomalyBlob::type)
                .contains("DUST", "SCRATCH");
    }

    @Test
    void underexposedOverexposedAndColorCastFramesAreClassifiedCorrectly() {
        // 1. Underexposed frame (mean luminance < 50)
        BufferedImage darkImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = 12;
                darkImg.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        Stage1AnalysisResult darkResult = analyzer.analyzeBufferedImage(darkImg, false);
        assertThat(darkResult.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_UNDEREXPOSED);

        // 2. Overexposed frame (mean luminance > 200)
        BufferedImage brightImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int v = 248;
                brightImg.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        Stage1AnalysisResult brightResult = analyzer.analyzeBufferedImage(brightImg, false);
        assertThat(brightResult.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_OVEREXPOSED);

        // 3. Heavy magenta/green color cast on crisp frame (color film vs B&W film)
        BufferedImage castImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                boolean even = ((x / 8) + (y / 8)) % 2 == 0;
                int r = even ? 210 : 150;
                int g = even ? 80 : 40;
                int b = even ? 190 : 130;
                castImg.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        Stage1AnalysisResult colorCastResult = analyzer.analyzeBufferedImage(castImg, false);
        assertThat(colorCastResult.colorCastDetected()).isTrue();
        assertThat(colorCastResult.metrics().colorCastCieScore()).isGreaterThan(18.0);
        assertThat(colorCastResult.primaryDefect()).isEqualTo(ClassicalCvAnalyzer.DEFECT_COLOR_CAST);

        Stage1AnalysisResult bwResult = analyzer.analyzeBufferedImage(castImg, true);
        assertThat(bwResult.colorCastDetected()).isFalse();
        assertThat(bwResult.metrics().colorCastCieScore()).isEqualTo(0.0);
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}
