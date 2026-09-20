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

    java.util.Optional<Notification> findByIdAndUser(Long id, User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByUser(User user);

    // =========================================
    // UNREAD COUNT
    // =========================================

    long countByUserAndReadFalse(User user);
}