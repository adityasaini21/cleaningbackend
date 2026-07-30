package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewResponseDTO;
import com.premchemicals.cleaningbackend.dto.ReviewSummaryDTO;

public interface ReviewService {

    ReviewResponseDTO addReview(
            ReviewRequestDTO request
    );

    ReviewSummaryDTO getProductReviews(
            Long productId
    );

    boolean canReview(
            Long productId
    );

    void markHelpful(
            Long reviewId
    );
}