package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.PaymentService;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/razorpay")
    public ResponseEntity<?> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {

        try {

            // 🔐 Verify webhook signature
            boolean isValid = Utils.verifyWebhookSignature(
                    payload,
                    signature,
                    webhookSecret
            );

            if (!isValid) {
                return ResponseEntity.badRequest()
                        .body("Invalid webhook signature");
            }

            JSONObject json = new JSONObject(payload);

            String event = json.getString("event");

            // We care about successful payments
            if ("payment.captured".equals(event)) {

                JSONObject paymentEntity =
                        json.getJSONObject("payload")
                                .getJSONObject("payment")
                                .getJSONObject("entity");

                String razorpayOrderId =
                        paymentEntity.getString("order_id");

                String razorpayPaymentId =
                        paymentEntity.getString("id");

                // 🔥 Mark payment success using existing service
                paymentService.handleWebhookSuccess(
                        razorpayOrderId,
                        razorpayPaymentId
                );
            }

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Webhook processing failed: " + e.getMessage());
        }
    }
}