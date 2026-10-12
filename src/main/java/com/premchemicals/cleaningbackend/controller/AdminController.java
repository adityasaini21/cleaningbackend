package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
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
    // TOGGLE USER ACTIVE STATUS
    // =========================================

    @PutMapping("/{userId}/toggle-status")
    public UserResponseDTO toggleStatus(
            @PathVariable Long userId
    ) {
        return adminUserService.toggleStatus(userId);
    }

    // =========================================
    // CLEAR BLOCKED USERS
    // =========================================

    @DeleteMapping("/clear-blocked")
    public String clearBlockedUsers() {
        adminUserService.clearBlockedUsers();
        return "Blocked users list cleared successfully";
    }
}