package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.ResetPasswordRequestDTO;
import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;

    // =========================================
    // SEARCH USERS
    // =========================================

    @GetMapping("/search")
    public List<UserResponseDTO> searchUsers(

            @RequestParam(defaultValue = "") String query
    ) {

        return adminUserService.searchUsers(query);
    }

    // =========================================
    // RESET USER PASSWORD
    // =========================================

    @PutMapping("/{userId}/reset-password")
    public String resetPassword(

            @PathVariable Long userId,

            @RequestBody ResetPasswordRequestDTO request
    ) {

        adminUserService.resetPassword(
                userId,
                request
        );

        return "Password reset successfully";
    }

    // =========================================
    // TOGGLE USER ACTIVE STATUS
    // =========================================

    @PutMapping("/{userId}/toggle-status")
    public UserResponseDTO toggleStatus(
            @PathVariable Long userId
    ) {
        return adminUserService.toggleStatus(userId);
    }
}