package com.premchemicals.cleaningbackend.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    @Value("${minimoth.api-key:}")
    private String minimothApiKey;

    @Value("${otp.dev-mode:false}")
    private boolean devMode;

    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private boolean isMockMode() {
        return devMode;
    }

    // Cache to store OTP codes in memory ONLY for dev-mode testing
    private final Map<String, OtpDetails> otpCache = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> otpCooldownMap = new ConcurrentHashMap<>();
    private final Map<String, PhoneLock> phoneLockMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> failedAttemptsMap = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lockoutMap = new ConcurrentHashMap<>();

    private static class PhoneLock {
        private int refCount = 0;
    }

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

        PhoneLock lock = phoneLockMap.compute(cleanPhone, (k, existingLock) -> {
            if (existingLock == null) {
                existingLock = new PhoneLock();
            }
            existingLock.refCount++;
            return existingLock;
        });

        try {
            synchronized (lock) {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime lastSent = otpCooldownMap.get(cleanPhone);

                if (lastSent != null) {
                    long secondsElapsed = Duration.between(lastSent, now).getSeconds();
                    if (secondsElapsed < COOLDOWN_SECONDS) {
                        long remainingSeconds = COOLDOWN_SECONDS - secondsElapsed;
                        throw new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Please wait " + remainingSeconds + " seconds before requesting another OTP."
                        );
                    } else {
                        otpCooldownMap.remove(cleanPhone);
                    }
                }

                if (isMockMode()) {
                    String otp = String.format("%06d", new Random().nextInt(999999));
                    // Save OTP to in-memory cache with 5-minute validity for dev-mode verification
                    otpCache.put(cleanPhone, new OtpDetails(otp, 5));
                    otpCooldownMap.put(cleanPhone, now);

                    System.out.println("==========================================");
                    System.out.println("🔥 [MOCK OTP MODE] Simulated OTP sent to " + cleanPhone + " is: " + otp);
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

                    otpCooldownMap.put(cleanPhone, now);
                    System.out.println("MiniMoth Send OTP Success: [REDACTED]");

                } catch (Exception e) {
                    throw new RuntimeException("Failed to send OTP via MiniMoth: " + e.getMessage(), e);
                }
            }
        } finally {
            phoneLockMap.compute(cleanPhone, (k, existingLock) -> {
                if (existingLock != null) {
                    existingLock.refCount--;
                    if (existingLock.refCount <= 0) {
                        return null;
                    }
                }
                return existingLock;
            });
        }
    }

    /**
     * Verifies if the provided OTP is valid.
     * In dev-mode, it verifies against our in-memory cache.
     * In production mode, it calls MiniMoth's POST /v1/otp/verify API.
     */
    public boolean verifyOtp(String phoneNumber, String otp) {
        String cleanPhone = normalizePhone(phoneNumber);

        PhoneLock lock = phoneLockMap.compute(cleanPhone, (k, existingLock) -> {
            if (existingLock == null) {
                existingLock = new PhoneLock();
            }
            existingLock.refCount++;
            return existingLock;
        });

        try {
            synchronized (lock) {
                // 1. Check if phone is currently locked out
                LocalDateTime lockoutExpiry = lockoutMap.get(cleanPhone);
                if (lockoutExpiry != null) {
                    if (LocalDateTime.now().isBefore(lockoutExpiry)) {
                        long remainingMinutes = Math.max(1, Duration.between(LocalDateTime.now(), lockoutExpiry).toMinutes() + 1);
                        throw new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too many failed attempts. Verification locked for " + remainingMinutes + " minutes."
                        );
                    } else {
                        // Expired lockout cleanup
                        lockoutMap.remove(cleanPhone);
                        failedAttemptsMap.remove(cleanPhone);
                    }
                }

                boolean isValid = false;

                if (isMockMode()) {
                    OtpDetails details = otpCache.get(cleanPhone);
                    if (details != null) {
                        if (details.isExpired()) {
                            otpCache.remove(cleanPhone);
                        } else if (details.getCode().equals(otp.trim())) {
                            isValid = true;
                        }
                    }
                } else {
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
                            isValid = true;
                        } else {
                            System.err.println("MiniMoth Verification Failed: Status " + response.statusCode());
                            isValid = false;
                        }

                    } catch (Exception e) {
                        System.err.println("Error verifying OTP with MiniMoth: " + e.getMessage());
                        isValid = false;
                    }
                }

                if (isValid) {
                    // Successful verification: clear failures, lockouts, and dev-mode cached token
                    failedAttemptsMap.remove(cleanPhone);
                    lockoutMap.remove(cleanPhone);
                    if (isMockMode()) {
                        otpCache.remove(cleanPhone);
                    }
                    return true;
                } else {
                    // Failed verification: increment failure count
                    int attempts = failedAttemptsMap.getOrDefault(cleanPhone, 0) + 1;
                    if (attempts >= MAX_FAILED_ATTEMPTS) {
                        // 5th failed attempt: invalidate OTP and lock out for 15 minutes
                        lockoutMap.put(cleanPhone, LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                        failedAttemptsMap.remove(cleanPhone);
                        if (isMockMode()) {
                            otpCache.remove(cleanPhone);
                        }
                    } else {
                        failedAttemptsMap.put(cleanPhone, attempts);
                    }
                    return false;
                }
            }
        } finally {
            phoneLockMap.compute(cleanPhone, (k, existingLock) -> {
                if (existingLock != null) {
                    existingLock.refCount--;
                    if (existingLock.refCount <= 0) {
                        return null;
                    }
                }
                return existingLock;
            });
        }
    }
}
