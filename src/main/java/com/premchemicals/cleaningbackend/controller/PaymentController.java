package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.PaymentService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor

public class PaymentController {

    private final PaymentService paymentService;

    // ==========================================
    // ✅ 1️⃣ CREATE RAZORPAY ORDER
    // ==========================================
    @PostMapping("/create/{orderId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> createRazorpayOrder(
            @PathVariable Long orderId) throws RazorpayException {

        String razorpayOrderId =
                paymentService.createRazorpayOrder(orderId);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Razorpay order created successfully",
                        "razorpayOrderId", razorpayOrderId
                )
        );
    }

    // ==========================================
    // ✅ 2️⃣ VERIFY PAYMENT (REAL FLOW)
    // ==========================================
    @PostMapping("/verify/{orderId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> verifyPayment(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> payload
    ) throws RazorpayException {

        String razorpayOrderId = payload.get("razorpay_order_id");
        String razorpayPaymentId = payload.get("razorpay_payment_id");
        String razorpaySignature = payload.get("razorpay_signature");

        if (razorpayOrderId == null ||
                razorpayPaymentId == null ||
                razorpaySignature == null) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payment payload"));
        }

        paymentService.verifyAndMarkPaymentSuccess(
                orderId,
                razorpayOrderId,
                razorpayPaymentId,
                razorpaySignature
        );

        return ResponseEntity.ok(
                Map.of("message", "Payment verified and marked SUCCESS")
        );
    }

    // ==========================================
    // ❌ 3️⃣ MARK PAYMENT FAILED
    // ==========================================
    @PostMapping("/failed/{orderId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> markPaymentFailed(
            @PathVariable Long orderId) {

        paymentService.markPaymentFailed(orderId);

        return ResponseEntity.ok(
                Map.of("message", "Payment marked as FAILED")
        );
    }

    // ==========================================
    // 📱 4️⃣ INITIATE PHONEPE PAYMENT
    // ==========================================
    @PostMapping("/phonepe/initiate/{orderId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> initiatePhonePePayment(
            @PathVariable Long orderId,
            HttpServletRequest request
    ) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        String backendBaseUrl = scheme + "://" + serverName + (serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort);

        String redirectUrl = paymentService.initiatePhonePePayment(orderId, backendBaseUrl);

        return ResponseEntity.ok(
                Map.of(
                        "message", "PhonePe payment initiated successfully",
                        "redirectUrl", redirectUrl
                )
        );
    }

    // ==========================================
    // 🌐 5️⃣ PHONEPE REDIRECT/CALLBACK
    // ==========================================
    @RequestMapping(value = "/phonepe/callback/{orderId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> phonePeCallback(
            @PathVariable Long orderId,
            @RequestParam(value = "transactionId", required = false) String merchantTransactionId
    ) {
        boolean isSuccess = paymentService.verifyPhonePePayment(orderId, merchantTransactionId);

        String statusClass = isSuccess ? "success" : "error";
        String statusText = isSuccess ? "Payment Successful" : "Payment Failed";
        String statusSubtext = isSuccess ? "Your order has been confirmed. You can now close this window." : "There was an issue processing your payment. Please try again.";

        String html = "<html>" +
                "<head>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; background-color: #121212; color: #ffffff; }" +
                ".card { text-align: center; padding: 40px; border-radius: 20px; background-color: #1c1c1e; box-shadow: 0 4px 20px rgba(0,0,0,0.3); border: 0.5px solid #2c2c2e; max-width: 90%; width: 350px; }" +
                ".icon { font-size: 60px; margin-bottom: 20px; }" +
                ".success { color: #30d158; }" +
                ".error { color: #ff453a; }" +
                "h2 { margin: 0 0 10px 0; font-size: 24px; font-weight: bold; }" +
                "p { margin: 0; color: #8e8e93; font-size: 15px; line-height: 1.4; }" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class='card'>" +
                "<div class='icon " + statusClass + "'>" + (isSuccess ? "✓" : "✗") + "</div>" +
                "<h2 class='" + statusClass + "'>" + statusText + "</h2>" +
                "<p id='status-msg'>" + statusSubtext + "</p>" +
                "</div>" +
                "</body>" +
                "</html>";

        return ResponseEntity.ok()
                .header("Content-Type", "text/html")
                .body(html);
    }
}