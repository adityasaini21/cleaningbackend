package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.Notification;
import com.premchemicals.cleaningbackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // =========================================
    // GET USER NOTIFICATIONS
    // =========================================

    List<Notification>
    findByUserOrderByCreatedAtDesc(User user);

    // =========================================
    // UNREAD COUNT
    // =========================================

    long countByUserAndReadFalse(User user);
}