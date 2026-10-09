package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final UserRepository userRepository;

    // =========================================
    // SEND OTP
    // =========================================
    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestParam String phoneNumber) {
        String rawPhone = phoneNumber != null ? phoneNumber.trim() : "";
        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        if (!tenDigitPhone.matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid 10-digit Indian mobile number"));
        }

        User user = userRepository.findByPhoneNumber(tenDigitPhone)
                .orElseGet(() -> userRepository.findByPhoneNumber(rawPhone).orElse(null));
        if (user != null && !user.isActive() && !user.isDeletedByUser()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Your account has been suspended by administration. Please contact customer care for support."));
        }

        try {
            otpService.sendOtp(tenDigitPhone);
            return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // =========================================
    // VERIFY OTP
    // =========================================
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestParam String phoneNumber, @RequestParam String otp) {
        if (phoneNumber == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Phone number and OTP are required"));
        }

        String rawPhone = phoneNumber.trim();
        String tenDigitPhone = rawPhone.replaceAll("[^0-9]", "");
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        boolean isValid = otpService.verifyOtp(tenDigitPhone, otp) || otpService.verifyOtp(rawPhone, otp);
        if (isValid) {
            return ResponseEntity.ok(Map.of("message", "OTP verified successfully", "verified", true));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP", "verified", false));
        }
    }
}
