package com.premchemicals.cleaningbackend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import com.premchemicals.cleaningbackend.dto.NotificationResponseDTO;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // =========================================
    // GET TEST USER
    // =========================================

//    public User getTestUser() {
//
//        return userRepository
//                .findByPhoneNumber("9795611275")
//                .orElseThrow(() ->
//                        new RuntimeException("Admin user not found"));
//    }
public User getTestUser() {
    throw new UnsupportedOperationException("Not used");
}

    // =========================================
    // CREATE + SEND NOTIFICATION
    // =========================================

    public void createNotification(

            User user,
            String title,
            String message
    ) {

        // SAVE IN DATABASE

        com.premchemicals.cleaningbackend.model.Notification
                notificationEntity =
                com.premchemicals.cleaningbackend.model.Notification
                        .builder()
                        .user(user)
                        .title(title)
                        .message(message)
                        .read(false)
                        .build();

        notificationRepository.save(notificationEntity);

        // SEND PUSH NOTIFICATION

        try {

            if (user.getFcmToken() != null &&
                    !user.getFcmToken().isEmpty()) {

                Message firebaseMessage =
                        Message.builder()

                                .setToken(user.getFcmToken())

                                .setNotification(
                                        Notification.builder()
                                                .setTitle(title)
                                                .setBody(message)
                                                .build()
                                )

                                .putData("title", title)
                                .putData("body", message)

                                .build();

                String response =
                        FirebaseMessaging.getInstance()
                                .send(firebaseMessage);

                System.out.println(
                        "FCM SENT SUCCESSFULLY: " + response
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "FCM SEND ERROR: " + e.getMessage()
            );
        }
    }

    // =========================================
    // GET MY NOTIFICATIONS
    // =========================================

    public List<NotificationResponseDTO>
    getMyNotifications() {

        User user = userRepository
                .findByPhoneNumber(getLoggedInPhoneNumber())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return notificationRepository
                .findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    // =========================================
    // MARK AS READ
    // =========================================

    public void markAsRead(Long notificationId) {

        com.premchemicals.cleaningbackend.model.Notification
                notification =
                notificationRepository
                        .findById(notificationId)
                        .orElseThrow(() ->
                                new RuntimeException("Notification not found"));

        notification.setRead(true);

        notificationRepository.save(notification);
    }

    // =========================================
    // UNREAD COUNT
    // =========================================

    public long getUnreadCount() {

        User user = userRepository
                .findByPhoneNumber(getLoggedInPhoneNumber())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return notificationRepository
                .countByUserAndReadFalse(user);
    }

    // =========================================
    // DTO MAPPER
    // =========================================

    private NotificationResponseDTO
    mapToDTO(
            com.premchemicals.cleaningbackend.model.Notification
                    notification
    ) {

        return NotificationResponseDTO
                .builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    // =========================================
    // HELPER
    // =========================================

    private String getLoggedInPhoneNumber() {

        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
    }
}