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
import com.premchemicals.cleaningbackend.dto.ReviewSummaryDTO;
import com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository;
import com.premchemicals.cleaningbackend.model.ReviewHelpful;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl
        implements ReviewService {

    private final ReviewRepository reviewRepository;

    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    private final OrderRepository orderRepository;
    private final ReviewHelpfulRepository reviewHelpfulRepository;

    // =========================================
    // ADD REVIEW
    // =========================================

    @Override
    @Transactional
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
                .username(user.getUsername())
                .rating(saved.getRating())
                .comment(saved.getComment())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .verifiedPurchase(true)
                .build();
    }

    // =========================================
    // GET PRODUCT REVIEWS
    // =========================================

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryDTO getProductReviews(
            Long productId
    ) {

        Product product =
                productRepository
                        .findById(productId)
                        .orElseThrow();

        List<Review> reviews =
                reviewRepository.findByProduct(product);

        int reviewCount = reviews.size();

        double averageRating =
                reviews.stream()
                        .mapToInt(Review::getRating)
                        .average()
                        .orElse(0.0);

        int fiveStar = (int) reviews.stream()
                .filter(r -> r.getRating() == 5)
                .count();

        int fourStar = (int) reviews.stream()
                .filter(r -> r.getRating() == 4)
                .count();

        int threeStar = (int) reviews.stream()
                .filter(r -> r.getRating() == 3)
                .count();

        int twoStar = (int) reviews.stream()
                .filter(r -> r.getRating() == 2)
                .count();

        int oneStar = (int) reviews.stream()
                .filter(r -> r.getRating() == 1)
                .count();

        List<ReviewResponseDTO> reviewDTOs =
                reviews.stream()
                        .map(review -> {

                            Authentication authentication =
                                    SecurityContextHolder
                                            .getContext()
                                            .getAuthentication();

                            User currentUser = null;

                            if (authentication != null &&
                                    authentication.isAuthenticated() &&
                                    !"anonymousUser".equals(authentication.getName())) {

                                currentUser = userRepository
                                        .findByUsername(authentication.getName())
                                        .orElse(null);
                            }

                            long helpfulCount =
                                    reviewHelpfulRepository
                                            .countByReview(review);

                            boolean helpfulByCurrentUser =
                                    currentUser != null &&
                                            reviewHelpfulRepository.existsByReviewAndUser(
                                                    review,
                                                    currentUser
                                            );

                            return ReviewResponseDTO.builder()

                                    .id(review.getId())

                                    .username(review.getUser().getUsername())

                                    .rating(review.getRating())

                                    .comment(review.getComment())

                                    .createdAt(review.getCreatedAt())

                                    .updatedAt(review.getUpdatedAt())

                                    .verifiedPurchase(true)

                                    .helpfulCount(helpfulCount)

                                    .helpfulByCurrentUser(helpfulByCurrentUser)

                                    .build();
                        })
                        .toList();

        return ReviewSummaryDTO
                .builder()
                .averageRating(
                        Math.round(averageRating * 10.0) / 10.0
                )
                .reviewCount(reviewCount)
                .fiveStar(fiveStar)
                .fourStar(fourStar)
                .threeStar(threeStar)
                .twoStar(twoStar)
                .oneStar(oneStar)
                .reviews(reviewDTOs)
                .build();
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

    @Override
    @Transactional
    public void markHelpful(Long reviewId) {

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

        Review review =
                reviewRepository
                        .findById(reviewId)
                        .orElseThrow();

        boolean alreadyMarked =
                reviewHelpfulRepository
                        .existsByReviewAndUser(
                                review,
                                user
                        );

        if (alreadyMarked) {

            return;
        }

        ReviewHelpful helpful =
                ReviewHelpful.builder()

                        .review(review)

                        .user(user)

                        .build();

        reviewHelpfulRepository.save(helpful);
    }
}