package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewResponseDTO;

import java.util.List;

public interface ReviewService {

    ReviewResponseDTO addReview(
            ReviewRequestDTO request
    );

    List<ReviewResponseDTO> getProductReviews(
            Long productId
    );
    boolean canReview(
            Long productId
    );
}