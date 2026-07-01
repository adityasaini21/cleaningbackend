package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.ReviewHelpful;
import com.premchemicals.cleaningbackend.model.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulRepository
        extends JpaRepository<ReviewHelpful, Long> {

    // =========================================
    // CHECK IF USER ALREADY MARKED HELPFUL
    // =========================================

    boolean existsByReviewAndUser(
            Review review,
            User user
    );

    // =========================================
    // COUNT HELPFUL VOTES
    // =========================================

    long countByReview(
            Review review
    );
}