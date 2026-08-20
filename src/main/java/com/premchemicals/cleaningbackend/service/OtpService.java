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

    @Value("${sms.fast2sms.api-key:}")
    private String fast2smsApiKey;

    // Cache to store OTP codes in memory (phone_number -> OtpDetails)
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
     * Generates and sends a 6-digit OTP to the phone number.
     * In dev-mode, it prints to console. In prod-mode, it calls Fast2SMS.
     */
    public void sendOtp(String phoneNumber) {
        String cleanPhone = phoneNumber.trim();
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Save OTP to in-memory cache with 5-minute validity
        otpCache.put(cleanPhone, new OtpDetails(otp, 5));

        if (devMode) {
            System.out.println("==========================================");
            System.out.println("🔥 [DEV MODE] OTP sent to " + cleanPhone + " is: " + otp);
            System.out.println("==========================================");
            return;
        }

        // Production Mode: Send SMS via Fast2SMS API
        if (fast2smsApiKey == null || fast2smsApiKey.isBlank()) {
            throw new RuntimeException("SMS API Key is not configured on the server.");
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            // Fast2SMS OTP Route URL
            String url = "https://www.fast2sms.com/dev/bulkV2?authorization=" + fast2smsApiKey
                    + "&variables_values=" + otp
                    + "&route=otp"
                    + "&numbers=" + cleanPhone;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Fast2SMS API returned status: " + response.statusCode());
            }
            System.out.println("Fast2SMS API Response: " + response.body());

        } catch (Exception e) {
            throw new RuntimeException("Failed to send OTP SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies if the provided OTP is valid and not expired.
     */
    public boolean verifyOtp(String phoneNumber, String otp) {
        String cleanPhone = phoneNumber.trim();
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
}
