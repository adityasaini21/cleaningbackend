package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.NotificationResponseDTO;
import com.premchemicals.cleaningbackend.service.NotificationService;
import com.premchemicals.cleaningbackend.model.User;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // =========================================
    // GET MY NOTIFICATIONS
    // =========================================

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NotificationResponseDTO>
    getMyNotifications() {

        return notificationService
                .getMyNotifications();
    }

    // =========================================
    // MARK AS READ
    // =========================================

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> markAsRead(
            @PathVariable Long id
    ) {

        notificationService.markAsRead(id);

        return Map.of(
                "message",
                "Notification marked as read"
        );
    }



    // =========================================
    // UNREAD COUNT
    // =========================================

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> getUnreadCount() {

        long count =
                notificationService.getUnreadCount();

        return Map.of(
                "count",
                count
        );
    }

    // =========================================
    // DELETE NOTIFICATION
    // =========================================
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> deleteNotification(
            @PathVariable Long id
    ) {
        notificationService.deleteNotification(id);
        return Map.of(
                "message",
                "Notification deleted successfully"
        );
    }
}