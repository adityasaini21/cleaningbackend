package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.mapper.UserMapper;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public List<UserResponseDTO> searchUsers(String query) {
        List<User> users = userRepository.searchUsers(query);

        // Auto-migrate legacy deleted users to deletedByUser = true
        for (User u : users) {
            if (!u.isActive() && !u.isDeletedByUser()) {
                if ("Deleted User".equalsIgnoreCase(u.getFullName()) ||
                    "[DELETED]".equals(u.getPassword()) ||
                    (u.getEmail() != null && u.getEmail().startsWith("deleted-"))) {
                    u.setDeletedByUser(true);
                    userRepository.save(u);
                }
            }
        }

        return users.stream()
                .map(userMapper::toDTO)
                .toList();
    }

    public void clearBlockedUsers() {
        List<User> allUsers = userRepository.findByRole(Role.ROLE_USER);
        List<User> blockedUsers = allUsers.stream()
                .filter(u -> !u.isActive() && !u.isDeletedByUser())
                .toList();

        if (!blockedUsers.isEmpty()) {
            userRepository.deleteAll(blockedUsers);
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