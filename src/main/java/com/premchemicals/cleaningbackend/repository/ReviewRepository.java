package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}