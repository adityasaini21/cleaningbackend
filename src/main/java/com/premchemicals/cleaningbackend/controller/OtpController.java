package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    // =========================================
    // SEND OTP
    // =========================================
    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestParam String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.trim().matches("^[6-9]\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid 10-digit Indian mobile number"));
        }

        try {
            otpService.sendOtp(phoneNumber);
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

        boolean isValid = otpService.verifyOtp(phoneNumber, otp);
        if (isValid) {
            return ResponseEntity.ok(Map.of("message", "OTP verified successfully", "verified", true));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP", "verified", false));
        }
    }
}
