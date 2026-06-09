package com.premchemicals.cleaningbackend.service.impl;

import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewResponseDTO;

import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.User;

import com.premchemicals.cleaningbackend.repository.ProductRepository;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import com.premchemicals.cleaningbackend.service.ReviewService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl
        implements ReviewService {

    private final ReviewRepository reviewRepository;

    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    private final OrderRepository orderRepository;

    // =========================================
    // ADD REVIEW
    // =========================================

    @Override
    public ReviewResponseDTO addReview(
            ReviewRequestDTO request) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String username =
                authentication.getName();

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        Product product =
                productRepository
                        .findById(request.getProductId())
                        .orElseThrow();

        boolean purchased =
                orderRepository
                        .hasPurchasedProduct(
                                user.getId(),
                                product.getId()
                        );

        if (!purchased) {

            throw new RuntimeException(
                    "You can review only purchased products"
            );
        }

        Review review =
                reviewRepository
                        .findByUserAndProduct(
                                user,
                                product
                        )
                        .orElse(
                                Review.builder()
                                        .user(user)
                                        .product(product)
                                        .build()
                        );

        review.setRating(
                request.getRating()
        );

        review.setComment(
                request.getComment()
        );

        Review saved =
                reviewRepository.save(review);

        return ReviewResponseDTO
                .builder()
                .id(saved.getId())
                .username(
                        saved.getUser()
                                .getUsername()
                )
                .rating(saved.getRating())
                .comment(saved.getComment())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    // =========================================
    // GET PRODUCT REVIEWS
    // =========================================

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponseDTO>
    getProductReviews(Long productId) {

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow();

        return reviewRepository
                .findByProduct(product)
                .stream()
                .map(review ->
                        ReviewResponseDTO
                                .builder()
                                .id(review.getId())
                                .username(
                                        review.getUser()
                                                .getUsername()
                                )
                                .rating(
                                        review.getRating()
                                )
                                .comment(
                                        review.getComment()
                                )
                                .createdAt(
                                        review.getCreatedAt()
                                )
                                .build()
                )
                .toList();
    }

    @Override
    public boolean canReview(
            Long productId
    ) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        String username =
                authentication.getName();

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow();

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow();

        return orderRepository
                .hasPurchasedProduct(
                        user.getId(),
                        product.getId()
                );
    }
}