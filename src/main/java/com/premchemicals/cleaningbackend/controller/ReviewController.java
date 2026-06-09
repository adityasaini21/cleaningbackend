package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewResponseDTO;
import com.premchemicals.cleaningbackend.service.ReviewService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // ======================================
    // ADD REVIEW
    // ======================================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ReviewResponseDTO addReview(

            @RequestBody
            ReviewRequestDTO request
    ) {

        return reviewService.addReview(
                request
        );
    }

    // ======================================
    // PRODUCT REVIEWS
    // ======================================

    @GetMapping("/product/{productId}")
    public List<ReviewResponseDTO> getProductReviews(

            @PathVariable Long productId
    ) {

        return reviewService
                .getProductReviews(productId);
    }
    @GetMapping("/can-review/{productId}")
    @PreAuthorize("isAuthenticated()")
    public boolean canReview(

            @PathVariable
            Long productId
    ) {

        return reviewService.canReview(
                productId
        );
    }
}