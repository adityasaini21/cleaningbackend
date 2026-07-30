package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "review_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================================
    // IMAGE URL (Cloudinary)
    // ==================================

    @Column(nullable = false, length = 1000)
    private String imageUrl;

    // ==================================
    // REVIEW
    // ==================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "review_id",
            nullable = false
    )
    private Review review;
}