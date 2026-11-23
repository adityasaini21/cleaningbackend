package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.mapper.UserMapper;
import com.premchemicals.cleaningbackend.model.Order;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewHelpfulRepository reviewHelpfulRepository;
    private final NotificationRepository notificationRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final Pattern INDIAN_PHONE_PATTERN = Pattern.compile("([6-9]\\d{9})");

    public List<UserResponseDTO> searchUsers(String query) {
        List<User> users = userRepository.searchUsers(query);

        return users.stream()
                .map(this::mapUserToAdminDTO)
                .toList();
    }

    private UserResponseDTO mapUserToAdminDTO(User user) {
        UserResponseDTO dto = userMapper.toDTO(user);

        if (user.isDeletedByUser() || (user.getPhoneNumber() != null && user.getPhoneNumber().startsWith("DELETED_"))) {
            dto.setDeletedByUser(true);
            String rawPhone = user.getPhoneNumber();
            String extractedPhone = null;

            if (rawPhone != null) {
                Matcher m = INDIAN_PHONE_PATTERN.matcher(rawPhone);
                if (m.find()) {
                    extractedPhone = m.group(1);
                }
            }

            // Fallback: check historical orders for user's phone number
            if (extractedPhone == null) {
                try {
                    List<Order> orders = orderRepository.findByUser(user);
                    if (orders != null && !orders.isEmpty()) {
                        for (int i = orders.size() - 1; i >= 0; i--) {
                            Order o = orders.get(i);
                            if (o.getPhoneNumber() != null && o.getPhoneNumber().matches("^[6-9]\\d{9}$")) {
                                extractedPhone = o.getPhoneNumber();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not resolve historical phone for deleted user {}: {}", user.getId(), e.getMessage());
                }
            }

            if (extractedPhone != null) {
                dto.setPhoneNumber(extractedPhone);
            }
        }
        return dto;
    }

    @Transactional
    public void clearBlockedUsers() {
        List<User> allUsers = userRepository.findByRole(Role.ROLE_USER);
        List<User> blockedUsers = allUsers.stream()
                .filter(u -> !u.isActive() && !u.isDeletedByUser())
                .toList();

        if (blockedUsers.isEmpty()) {
            return;
        }

        List<User> safeToDelete = new ArrayList<>();
        int preservedCount = 0;

        for (User user : blockedUsers) {
            boolean hasOrders = orderRepository.existsByUser(user);
            boolean hasReviews = reviewRepository.existsByUser(user);
            boolean hasHelpfulVotes = reviewHelpfulRepository.existsByUser(user);

            if (hasOrders || hasReviews || hasHelpfulVotes) {
                preservedCount++;
                log.info("Preserving blocked user id={} to maintain historical records and avoid foreign-key violations (orders={}, reviews={}, helpfulVotes={})",
                        user.getId(), hasOrders, hasReviews, hasHelpfulVotes);
            } else {
                safeToDelete.add(user);
            }
        }

        if (!safeToDelete.isEmpty()) {
            for (User user : safeToDelete) {
                notificationRepository.deleteByUser(user);
            }
            userRepository.deleteAll(safeToDelete);
            log.info("Safely deleted {} blocked users without historical records; preserved {} blocked users with business records",
                    safeToDelete.size(), preservedCount);
        }
    }



    public UserResponseDTO toggleStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        user.setActive(!user.isActive());
        user.setDeletedByUser(false);

        // Sanitize legacy fields so JPA bean validation never fails on save
        if (user.getPhoneNumber() != null) {
            String cleanPhone = user.getPhoneNumber().replaceAll("[^0-9]", "");
            if (cleanPhone.length() > 10) {
                cleanPhone = cleanPhone.substring(cleanPhone.length() - 10);
            }
            if (cleanPhone.matches("^[6-9]\\d{9}$")) {
                user.setPhoneNumber(cleanPhone);
            }
        }
        if (user.getEmail() != null && (user.getEmail().isBlank() || !user.getEmail().contains("@"))) {
            user.setEmail(null);
        }
        if (user.getPincode() != null && !user.getPincode().matches("^\\d{6}$")) {
            user.setPincode(null);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toDTO(savedUser);
    }
}