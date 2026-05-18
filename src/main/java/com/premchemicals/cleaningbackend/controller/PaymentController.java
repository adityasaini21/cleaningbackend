package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.PaymentService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
}