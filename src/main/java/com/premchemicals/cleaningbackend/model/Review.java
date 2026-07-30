package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "user_id",
                                "product_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==================================
    // RATING
    // ==================================

    @Column(nullable = false)
    private Integer rating;

    // ==================================
    // COMMENT
    // ==================================

    @Column(length = 1000)
    private String comment;

    // ==================================
    // DATES
    // ==================================

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    // ==================================
    // USER
    // ==================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    // ==================================
    // PRODUCT
    // ==================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    // ==================================
    // REVIEW IMAGES
    // ==================================

    @OneToMany(

            mappedBy = "review",

            cascade = CascadeType.ALL,

            orphanRemoval = true,

            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<ReviewImage> images = new ArrayList<>();

    // ==================================
    // TIMESTAMPS
    // ==================================

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {

        updatedAt = LocalDateTime.now();
    }
}