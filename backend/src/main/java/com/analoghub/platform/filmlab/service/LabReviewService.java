package com.analoghub.platform.filmlab.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabReviewService {

    @Transactional
    public void postReview(Long labId, Long orderId, int rating, String comment) {
        // 1. Kiểm tra xem orderId này đã được review chưa (1 review / orderId)
        // 2. Lưu review vào cơ sở dữ liệu
        // 3. Tính toán lại avg_rating của Lab
        // 4. Phát sự kiện LabReviewPostedEvent
    }
}