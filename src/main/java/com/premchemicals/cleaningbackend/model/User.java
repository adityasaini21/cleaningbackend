package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            unique = true,
            nullable = false
    )
    private String username;

    @Column(nullable = false)
    private String password;

    // ROLE_USER / ROLE_ADMIN
    private String role;

    // =========================================
    // 🔥 FCM TOKEN
    // =========================================

    @Column(length = 1000)
    private String fcmToken;
}