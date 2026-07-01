package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewResponseDTO;
import com.premchemicals.cleaningbackend.dto.ReviewSummaryDTO;

import java.util.List;

public interface ReviewService {



    ReviewResponseDTO addReview(
            ReviewRequestDTO request
    );

    ReviewSummaryDTO getProductReviews(Long productId);
    boolean canReview(
            Long productId
    );
    // =========================================

    // 👍 MARK REVIEW AS HELPFUL

    // =========================================

    void markHelpful(

            Long reviewId

    );
}