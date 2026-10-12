package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository
        extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {
            "user",
            "product"
    })
    List<Review> findByProduct(Product product);


    Optional<Review> findByUserAndProduct(
            User user,
            Product product
    );

    boolean existsByUserAndProduct(
            User user,
            Product product
    );

    boolean existsByUser(User user);

    // =========================================
    // RATING AGGREGATION
    // =========================================

    @Query("""
            SELECT AVG(r.rating)
            FROM Review r
            WHERE r.product.id = :productId
            """)
    Double getAverageRating(Long productId);

    @Query("""
            SELECT COUNT(r)
            FROM Review r
            WHERE r.product.id = :productId
            """)
    Long getReviewCount(Long productId);

    @Query("""
            SELECT r.product.id, AVG(r.rating), COUNT(r)
            FROM Review r
            WHERE r.product.id IN :productIds
            GROUP BY r.product.id
            """)
    List<Object[]> getRatingStatsForProductIds(@org.springframework.data.repository.query.Param("productIds") List<Long> productIds);
}