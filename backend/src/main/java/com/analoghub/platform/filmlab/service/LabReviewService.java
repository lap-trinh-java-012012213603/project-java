package com.analoghub.platform.filmlab.service;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.common.exception.ResourceNotFoundException;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabReview;
import com.analoghub.platform.filmlab.dto.PostLabReviewRequest;
import com.analoghub.platform.filmlab.event.LabReviewPostedEvent;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.LabReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LabReviewService {

    private final LabReviewRepository labReviewRepository;
    private final LabRepository labRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LabReview postReview(UUID labId, UUID customerId, PostLabReviewRequest request) {
        // 1. Kiểm tra Lab có tồn tại không
        Lab lab = labRepository.findById(labId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 2. Kiểm tra xem order này đã được review chưa (Quy tắc 1 review / orderId)
        if (labReviewRepository.existsByOrderId(request.getOrderId())) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED_LAB, "Order has already been reviewed");
        }

        // 3. Lưu review mới
        LabReview review = LabReview.builder()
                .labId(labId)
                .customerId(customerId)
                .orderId(request.getOrderId())
                .rating(request.getRating())
                .comment(request.getComment())
                .moderationStatus("PUBLISHED")
                .build();

        labReviewRepository.save(review);

        // 4. Cập nhật lại review_count và avg_rating cho Lab
        int newReviewCount = lab.getReviewCount() + 1;

        // Công thức tính trung bình sao mới
        BigDecimal currentTotalScore = lab.getAvgRating().multiply(BigDecimal.valueOf(lab.getReviewCount()));
        BigDecimal newTotalScore = currentTotalScore.add(BigDecimal.valueOf(request.getRating()));
        BigDecimal newAvgRating = newTotalScore.divide(BigDecimal.valueOf(newReviewCount), 2, RoundingMode.HALF_UP);

        lab.setReviewCount(newReviewCount);
        lab.setAvgRating(newAvgRating);
        labRepository.save(lab);

        // 5. Phát sự kiện
        eventPublisher.publishEvent(new LabReviewPostedEvent(review.getId(), labId, request.getOrderId()));

        return review;
    }
}