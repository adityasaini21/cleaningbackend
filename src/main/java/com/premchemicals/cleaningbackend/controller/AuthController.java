package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.LoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.LoginResponseDTO;
import com.premchemicals.cleaningbackend.dto.SaveFcmTokenRequest;
import com.premchemicals.cleaningbackend.dto.UserProfileDTO;
import com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.security.JwtUtil;
import com.premchemicals.cleaningbackend.security.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.service.OtpService;
import java.util.UUID;
import java.util.Map;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final OtpService otpService;

    private final TokenBlacklistService tokenBlacklistService;

    @Autowired(required = false)
    private NotificationRepository notificationRepository;

    // =========================================
    // REGISTER CUSTOMER
    // =========================================

    @PostMapping("/register")
    public String register(
            @RequestBody User request
    ) {

        String rawPhone = request.getPhoneNumber().trim();
        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }
        String cleanPhone = tenDigitPhone.isEmpty() ? rawPhone : tenDigitPhone;

        User existing = userRepository.findByCanonicalPhone(cleanPhone, rawPhone).orElse(null);
        if (existing != null) {
            if (!existing.isActive() && !existing.isDeletedByUser()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Account has been suspended by administration");
            }
            if (existing.isDeletedByUser()) {
                existing.setPhoneNumber("DELETED_" + existing.getId() + "_" + System.currentTimeMillis());
                userRepository.save(existing);
            } else {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Phone number already registered");
            }
        } else if (userRepository.existsByCanonicalPhone(cleanPhone, rawPhone)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Phone number already registered");
        }

        User user = new User();

        user.setPhoneNumber(cleanPhone);

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(Role.ROLE_USER);

        user.setActive(true);

        user.setFullName(
                request.getFullName().trim()
        );

        if (request.getEmail() != null) {
            user.setEmail(
                    request.getEmail().trim()
            );
        }

        if (request.getAddress() != null) {
            user.setAddress(
                    request.getAddress().trim()
            );
        }

        if (request.getCity() != null) {
            user.setCity(
                    request.getCity().trim()
            );
        }

        if (request.getState() != null) {
            user.setState(
                    request.getState().trim()
            );
        }

        if (request.getPincode() != null) {
            user.setPincode(
                    request.getPincode().trim()
            );
        }

        if (request.getLandmark() != null) {
            user.setLandmark(
                    request.getLandmark().trim()
            );
        }

        userRepository.save(user);

        return "User registered successfully";
    }

    // =========================================
    // LOGIN
    // =========================================

    @PostMapping("/login")
    public LoginResponseDTO login(
            @RequestBody LoginRequestDTO request
    ) {

        String rawPhone = request.getPhoneNumber().trim();
        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        Authentication authentication =
                authenticationManager.authenticate(

                        new UsernamePasswordAuthenticationToken(

                                tenDigitPhone,

                                request.getPassword()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

        String token =
                jwtUtil.generateToken(userDetails);

        return new LoginResponseDTO(token);
    }

    // =========================================
    // OTP PASSWORDLESS LOGIN & REGISTER
    // =========================================
    @PostMapping("/otp/login")
    public ResponseEntity<?> loginOrRegisterWithOtp(@Valid @RequestBody OtpLoginRequestDTO request) {
        String rawPhone = request.getPhoneNumber().trim();
        String otp = request.getOtp().trim();

        // 1. Verify OTP first
        boolean isOtpValid;
        try {
            isOtpValid = otpService.verifyOtp(rawPhone, otp);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("error", e.getReason() != null ? e.getReason() : "OTP verification rejected"));
        }
        if (!isOtpValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }

        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        String phone = tenDigitPhone;

        // 2. Check if user exists by phone
        User user = userRepository.findByCanonicalPhone(phone, rawPhone).orElse(null);
        boolean isNewUser = false;

        if (user != null) {
            // Existing user verified OTP
            if (!user.isActive() && !user.isDeletedByUser()) {
                return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Your account has been suspended by administration. Please contact customer care for support."));
            }

            // Deleted account: MUST NOT be reactivated or reused!
            // Detach/anonymize the deleted record and treat returning registration as a fresh user.
            if (user.isDeletedByUser()) {
                user.setPhoneNumber("DELETED_" + user.getId() + "_" + System.currentTimeMillis());
                user.setActive(false);
                user.setPassword("[DELETED]");
                userRepository.save(user);
                user = null; // triggers brand new user creation below
            }
        }

        if (user == null) {
            // User does not exist, so register them as a fresh user!
            String name = request.getFullName();
            if (name == null || name.trim().isEmpty()) {
                name = "Customer"; // Fallback name
            } else {
                name = name.trim();
            }

            user = User.builder()
                    .phoneNumber(phone)
                    .fullName(name)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.ROLE_USER)
                    .active(true)
                    .deletedByUser(false)
                    .build();

            userRepository.save(user);
            isNewUser = true;
        }

        // 3. Generate JWT Token
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(user.getPhoneNumber())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .authorities(user.getRole().name())
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new OtpLoginResponseDTO(token, isNewUser, user.getFullName()));
    }

    // =========================================
    // CHECK IF PHONE NUMBER EXISTS
    // =========================================
    @GetMapping("/check-phone")
    public ResponseEntity<?> checkPhoneExists(@RequestParam String phoneNumber) {
        String rawPhone = phoneNumber.trim();
        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        User user = userRepository.findByCanonicalPhone(tenDigitPhone, rawPhone).orElse(null);
        if (user != null && !user.isActive() && !user.isDeletedByUser()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Your account has been suspended by administration. Please contact customer care for support."));
        }
        boolean exists = (user != null && user.isActive() && !user.isDeletedByUser());
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        String rawPhone = authentication.getName();
        String tenDigitPhone = rawPhone != null ? rawPhone.replaceAll("[^0-9]", "") : "";
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }
        return userRepository.findByCanonicalPhone(tenDigitPhone, rawPhone)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    }

    // =========================================
    // SAVE FCM TOKEN
    // =========================================

    @PostMapping("/save-fcm-token")
    public String saveFcmToken(

            @RequestBody SaveFcmTokenRequest request,

            Authentication authentication
    ) {

        String phoneNumber =
                authentication.getName();

        User user = getAuthenticatedUser(authentication);

        if (request.getFcmToken() != null) {

            user.setFcmToken(
                    request.getFcmToken().trim()
            );

            userRepository.save(user);
        }

        return "FCM token saved successfully";
    }

    // =========================================
    // GET PROFILE
    // =========================================
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public UserProfileDTO getProfile(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);

        UserProfileDTO dto = new UserProfileDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setAddress(user.getAddress());
        dto.setCity(user.getCity());
        dto.setState(user.getState());
        dto.setPincode(user.getPincode());
        dto.setLandmark(user.getLandmark());
        return dto;
    }

    // =========================================
    // UPDATE PROFILE
    // =========================================
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public UserProfileDTO updateProfile(
            @RequestBody UserProfileDTO request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        user.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        user.setCity(request.getCity() != null ? request.getCity().trim() : null);
        user.setState(request.getState() != null ? request.getState().trim() : null);
        user.setPincode(request.getPincode() != null ? request.getPincode().trim() : null);
        user.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);

        userRepository.save(user);
        return request;
    }

    // =========================================
    // CHANGE PASSWORD (ADMIN ONLY)
    // =========================================
    @PutMapping("/change-password")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')")
    public String changePassword(
            @Valid @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        if (user.getRole() != Role.ROLE_ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Only administrators can change passwords");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Incorrect old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        return "Password changed successfully";
    }

    // =========================================
    // DELETE ACCOUNT (ANONYMIZE PII)
    // =========================================
    @DeleteMapping("/delete-account")
    @PreAuthorize("isAuthenticated()")
    public String deleteAccount(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            Authentication authentication
    ) {
        User user = getAuthenticatedUser(authentication);

        // 1. Blacklist token immediately
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                java.util.Date expiration = jwtUtil.extractExpiration(jwt);
                tokenBlacklistService.blacklistToken(jwt, expiration.getTime());
            } catch (Exception e) {
                // Token parsing failed, continue
            }
        }

        // 2. Set passwordChangedAt to invalidate any active sessions
        user.setPasswordChangedAt(java.time.LocalDateTime.now());

        // 3. Clear ephemeral user notifications
        if (notificationRepository != null) {
            try {
                notificationRepository.deleteByUser(user);
            } catch (Exception e) {
                // Ignore if notifications repo fails
            }
        }

        // 4. Anonymize user details and detach phone number
        user.setActive(false);
        user.setDeletedByUser(true);
        user.setFullName("Deleted User");
        user.setAddress(null);
        user.setCity(null);
        user.setState(null);
        user.setPincode(null);
        user.setLandmark(null);
        user.setFcmToken(null);
        user.setEmail(null);
        user.setPassword("[DELETED]");
        user.setPhoneNumber("DELETED_" + user.getId() + "_" + System.currentTimeMillis());

        userRepository.save(user);
        return "Account deactivated and user data cleared successfully";
    }

    // =========================================
    // LOGOUT (BLACKLIST JWT)
    // =========================================
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                java.util.Date expiration = jwtUtil.extractExpiration(jwt);
                tokenBlacklistService.blacklistToken(jwt, expiration.getTime());
            } catch (Exception e) {
                System.err.println("Logout token parsing error: " + e.getMessage());
            }
        }
        return ResponseEntity.ok(java.util.Map.of("message", "Logged out successfully"));
    }
}