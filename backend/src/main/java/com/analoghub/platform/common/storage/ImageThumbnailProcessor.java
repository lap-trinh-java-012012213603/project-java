package com.analoghub.platform.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class ImageThumbnailProcessor {

    /**
     * Xử lý ảnh gốc (hỗ trợ TIFF thông qua TwelveMonkeys và các định dạng phổ biến)
     * để tạo ảnh thumbnail/preview định dạng WebP.
     */
    public byte[] resizeToWebpBytes(byte[] originalImageBytes, int targetWidth, int targetHeight, float quality) {
        try {
            // Đọc ảnh đầu vào (TwelveMonkeys SPI tự động đăng ký và đọc được TIFF)
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
            if (originalImage == null) {
                throw new IOException("Không thể giải mã dữ liệu ảnh đầu vào. Định dạng không được hỗ trợ hoặc file bị hỏng.");
            }

            // Tính toán kích thước thu nhỏ giữ nguyên tỉ lệ
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            double ratio = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
            int scaledWidth = (int) (originalWidth * ratio);
            int scaledHeight = (int) (originalHeight * ratio);

            // Resize ảnh chất lượng cao
            Image tmp = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            BufferedImage resizedImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D g2d = resizedImage.createGraphics();
            g2d.drawImage(tmp, 0, 0, null);
            g2d.dispose();

            // Ghi ra định dạng WebP
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean written = ImageIO.write(resizedImage, "webp", outputStream);
            if (!written) {
                // Fallback sang jpg nếu writer webp chưa sẵn sàng trong môi trường hiện tại
                log.warn("Không tìm thấy ImageWriter cho WebP, fallback sang định dạng JPEG.");
                ImageIO.write(resizedImage, "jpg", outputStream);
            }

            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Lỗi trong quá trình xử lý tạo thumbnail ảnh: {}", e.getMessage(), e);
            throw new RuntimeException("Xử lý thumbnail ảnh thất bại", e);
        }
    }
}