package com.analoghub.platform.common.storage;

<<<<<<< HEAD
import com.analoghub.platform.common.dto.ImageProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ImageThumbnailProcessor {

    private static final Logger log = LoggerFactory.getLogger(ImageThumbnailProcessor.class);

    public static final int DEFAULT_PREVIEW_MAX_DIMENSION = 2048;
    public static final int DEFAULT_THUMBNAIL_MAX_DIMENSION = 400;

    public ImageProcessResult processImage(byte[] sourceBytes) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(sourceBytes));
        if (originalImage == null) {
            throw new IllegalArgumentException("Unsupported image format or corrupted image stream");
        }

        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();

        BufferedImage previewImg = resizeToMaxDimension(originalImage, DEFAULT_PREVIEW_MAX_DIMENSION);
        BufferedImage thumbImg = resizeToMaxDimension(originalImage, DEFAULT_THUMBNAIL_MAX_DIMENSION);

        byte[] previewBytes = compressToWebP(previewImg);
        byte[] thumbBytes = compressToWebP(thumbImg);

        return new ImageProcessResult(previewBytes, thumbBytes, origWidth, origHeight, "webp");
    }

    public byte[] compressToWebP(BufferedImage image) throws IOException {
        BufferedImage rgbImage = toRgbBufferedImage(image);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        boolean written = ImageIO.write(rgbImage, "webp", outputStream);
        if (!written) {
            log.warn("WebP writer not registered in ImageIO SPI, falling back to JPEG");
            outputStream.reset();
            ImageIO.write(rgbImage, "jpeg", outputStream);
        }

        return outputStream.toByteArray();
    }

    public BufferedImage resizeToMaxDimension(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return original;
        }

        double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
        int targetWidth = (int) Math.round(width * scale);
        int targetHeight = (int) Math.round(height * scale);

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g2d.dispose();
        }

        return resized;
    }

    private BufferedImage toRgbBufferedImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        BufferedImage rgbImage = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g2d = rgbImage.createGraphics();
        try {
            g2d.drawImage(source, 0, 0, Color.WHITE, null);
        } finally {
            g2d.dispose();
        }

        return rgbImage;
    }
=======
public class ImageThumbnailProcessor {
    
>>>>>>> f764710a1bf036f55b692c38351864ccd306c790
}
