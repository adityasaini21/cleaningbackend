package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================
    // USER
    // =========================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // =========================================
    // TITLE
    // =========================================

    @Column(nullable = false)
    private String title;

    // =========================================
    // MESSAGE
    // =========================================

    @Column(nullable = false, length = 1000)
    private String message;

    // =========================================
    // READ STATUS
    // =========================================

    @Column(nullable = false)
    private boolean read = false;

    // =========================================
    // CREATED TIME
    // =========================================

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // =========================================
    // AUTO TIMESTAMP
    // =========================================

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}