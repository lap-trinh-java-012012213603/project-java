package com.analoghub.platform.digitalarchive.service;

import com.analoghub.platform.common.storage.StorageProperties;
import com.analoghub.platform.common.storage.StorageService;
import com.analoghub.platform.digitalarchive.domain.ArchiveFrame;
import com.analoghub.platform.digitalarchive.domain.ArchiveRoll;
import com.analoghub.platform.digitalarchive.repository.ArchiveFrameRepository;
import com.analoghub.platform.digitalarchive.repository.ArchiveRollRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactSheetGeneratorService {

    private static final int COLUMNS = 6;
    private static final int CELL_WIDTH = 260;
    private static final int CELL_HEIGHT = 200;
    private static final int PADDING = 20;
    private static final int HEADER_HEIGHT = 70;

    private final ArchiveRollRepository archiveRollRepository;
    private final ArchiveFrameRepository archiveFrameRepository;
    private final StorageService storageService;
    private final S3Client s3Client;
    private final StorageProperties storageProperties;

    @Transactional
    public String generateContactSheetForRoll(UUID archiveRollId) {
        ArchiveRoll roll = archiveRollRepository.findById(archiveRollId).orElse(null);
        if (roll == null) {
            return null;
        }

        List<ArchiveFrame> frames = archiveFrameRepository.findByArchiveRollIdOrderByFrameNumberAsc(archiveRollId);
        int totalFrames = Math.max(1, frames.size());
        int rows = (int) Math.ceil((double) totalFrames / COLUMNS);

        int canvasWidth = PADDING + COLUMNS * (CELL_WIDTH + PADDING);
        int canvasHeight = HEADER_HEIGHT + PADDING + rows * (CELL_HEIGHT + PADDING);

        BufferedImage sheet = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Nền đen chuẩn cuộn film âm bản
        g.setColor(new Color(18, 18, 18));
        g.fillRect(0, 0, canvasWidth, canvasHeight);

        // Tiêu đề cuộn film
        g.setColor(new Color(235, 175, 60));
        g.setFont(new Font("Monospaced", Font.BOLD, 20));
        String headerTitle = String.format("ANALOGHUB CONTACT SHEET | %s | PROCESS: %s | FRAMES: %d",
                roll.getRollTitle(),
                roll.getProcessType() != null ? roll.getProcessType() : "STD",
                frames.size());
        g.drawString(headerTitle, PADDING, 42);

        g.setFont(new Font("Monospaced", Font.PLAIN, 13));
        for (int i = 0; i < frames.size(); i++) {
            ArchiveFrame frame = frames.get(i);
            int col = i % COLUMNS;
            int row = i / COLUMNS;

            int x = PADDING + col * (CELL_WIDTH + PADDING);
            int y = HEADER_HEIGHT + PADDING + row * (CELL_HEIGHT + PADDING);

            // Khung viền từng frame
            g.setColor(new Color(38, 38, 38));
            g.fillRect(x, y, CELL_WIDTH, CELL_HEIGHT - 24);

            BufferedImage thumbImg = tryLoadThumbnail(frame.getS3KeyThumb());
            if (thumbImg != null) {
                g.drawImage(thumbImg, x + 4, y + 4, CELL_WIDTH - 8, CELL_HEIGHT - 32, null);
            } else {
                g.setColor(new Color(90, 90, 90));
                g.drawRect(x + 4, y + 4, CELL_WIDTH - 8, CELL_HEIGHT - 32);
                g.drawString("FRAME #" + frame.getFrameNumber(), x + 75, y + (CELL_HEIGHT - 24) / 2);
            }

            // Số thứ tự frame kiểu film rebate
            g.setColor(new Color(235, 175, 60));
            g.drawString(String.format("%02dA", frame.getFrameNumber()), x + 6, y + CELL_HEIGHT - 6);
        }
        g.dispose();

        String s3Key = String.format("private/tenants/%s/orders/%s/rolls/%s/contact_sheet.jpg",
                roll.getSourceLabId(), roll.getSourceOrderId(), roll.getSourceFilmRollId());

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(sheet, "jpg", baos);
            byte[] jpegBytes = baos.toByteArray();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.getBucketName())
                            .key(s3Key)
                            .contentType("image/jpeg")
                            .build(),
                    RequestBody.fromBytes(jpegBytes)
            );
        } catch (Exception e) {
            log.warn("Không thể đẩy contact sheet lên S3 (key={}), vẫn lưu key tham chiếu: {}", s3Key, e.getMessage());
        }

        roll.setContactSheetS3Key(s3Key);
        archiveRollRepository.save(roll);
        return s3Key;
    }

    private BufferedImage tryLoadThumbnail(String s3KeyThumb) {
        try {
            if (s3KeyThumb != null && storageService.exists(s3KeyThumb)) {
                byte[] bytes = storageService.downloadBytes(s3KeyThumb);
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}