package com.premchemicals.cleaningbackend.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    @Value("${payment.dev-mode:true}")
    private boolean devMode;

    @Value("${minimoth.api-key:}")
    private String minimothApiKey;

    // Cache to store OTP codes in memory ONLY for dev-mode testing
    private final Map<String, OtpDetails> otpCache = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private static class OtpDetails {
        private String code;
        private LocalDateTime expiryTime;

        public OtpDetails(String code, int expiryMinutes) {
            this.code = code;
            this.expiryTime = LocalDateTime.now().plusMinutes(expiryMinutes);
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
    }

    /**
     * Normalizes the phone number to international format (+91XXXXXXXXXX) for Indian numbers.
     */
    private String normalizePhone(String phoneNumber) {
        String clean = phoneNumber.trim().replaceAll("\\s+", "");
        if (clean.startsWith("+91")) {
            return clean;
        } else if (clean.startsWith("91") && clean.length() == 12) {
            return "+" + clean;
        } else if (clean.length() == 10) {
            return "+91" + clean;
        }
        return clean;
    }

    /**
     * Sends a 6-digit OTP to the phone number.
     * In dev-mode, it simulates OTP sending and prints it to the console.
     * In production mode, it calls MiniMoth's POST /v1/otp/send API.
     */
    public void sendOtp(String phoneNumber) {
        String cleanPhone = normalizePhone(phoneNumber);

        if (devMode) {
            String otp = String.format("%06d", new Random().nextInt(999999));
            // Save OTP to in-memory cache with 5-minute validity for dev-mode verification
            otpCache.put(cleanPhone, new OtpDetails(otp, 5));

            System.out.println("==========================================");
            System.out.println("🔥 [DEV MODE] Simulated OTP sent to " + cleanPhone + " is: " + otp);
            System.out.println("==========================================");
            return;
        }

        // Production Mode: Send OTP via MiniMoth API
        if (minimothApiKey == null || minimothApiKey.isBlank()) {
            throw new RuntimeException("MiniMoth API Key is not configured on the server.");
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonPayload = String.format("{\"phone\":\"%s\"}", cleanPhone);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minimoth.dev/v1/otp/send"))
                    .header("X-Api-Key", minimothApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("MiniMoth Send OTP Error: Status " + response.statusCode());
                throw new RuntimeException("MiniMoth API failed to send OTP. Code: " + response.statusCode());
            }

            System.out.println("MiniMoth Send OTP Success: [REDACTED]");

        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP via MiniMoth: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies if the provided OTP is valid.
     * In dev-mode, it verifies against our in-memory cache.
     * In production mode, it calls MiniMoth's POST /v1/otp/verify API.
     */
    public boolean verifyOtp(String phoneNumber, String otp) {
        String cleanPhone = normalizePhone(phoneNumber);

        if (devMode) {
            OtpDetails details = otpCache.get(cleanPhone);
            if (details == null) {
                return false;
            }
            if (details.isExpired()) {
                otpCache.remove(cleanPhone);
                return false;
            }
            boolean isValid = details.getCode().equals(otp.trim());
            if (isValid) {
                otpCache.remove(cleanPhone); // Clear token after successful verification
            }
            return isValid;
        }

        // Production Mode: Verify OTP via MiniMoth API
        if (minimothApiKey == null || minimothApiKey.isBlank()) {
            throw new RuntimeException("MiniMoth API Key is not configured on the server.");
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonPayload = String.format("{\"phone\":\"%s\",\"code\":\"%s\"}", cleanPhone, otp.trim());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minimoth.dev/v1/otp/verify"))
                    .header("X-Api-Key", minimothApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("MiniMoth Verification Success: [REDACTED]");
                return true;
            } else {
                System.err.println("MiniMoth Verification Failed: Status " + response.statusCode());
                return false;
            }

        } catch (Exception e) {
            System.err.println("Error verifying OTP with MiniMoth: " + e.getMessage());
            return false;
        }
    }
}
