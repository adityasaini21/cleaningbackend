package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.ReviewImage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewImageRepository
        extends JpaRepository<ReviewImage, Long> {

    // ======================================
    // GET ALL IMAGES OF A REVIEW
    // ======================================

    List<ReviewImage> findByReview(
            Review review
    );

    // ======================================
    // DELETE ALL IMAGES OF A REVIEW
    // ======================================

    void deleteByReview(
            Review review
    );
}