package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.PaymentService;
import com.razorpay.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Value("${razorpay.webhook_secret}")
    private String webhookSecret;

    // =====================================
    // ✅ RAZORPAY WEBHOOK ENDPOINT
    // =====================================
    @PostMapping("/webhook")
    public String handleWebhook(
            HttpServletRequest request,
            @RequestHeader("X-Razorpay-Signature") String razorpaySignature
    ) throws Exception {

        String payload = getBody(request);

        // 🔐 Verify webhook signature
        boolean isValid = Utils.verifyWebhookSignature(
                payload,
                razorpaySignature,
                webhookSecret
        );

        if (!isValid) {
            throw new RuntimeException("Invalid Razorpay webhook signature");
        }

        JSONObject jsonObject = new JSONObject(payload);
        String event = jsonObject.getString("event");

        // =====================================
        // ✅ HANDLE PAYMENT CAPTURED EVENT
        // =====================================
        if ("payment.captured".equals(event)) {

            JSONObject paymentEntity =
                    jsonObject.getJSONObject("payload")
                            .getJSONObject("payment")
                            .getJSONObject("entity");

            String razorpayOrderId =
                    paymentEntity.getString("order_id");

            String razorpayPaymentId =
                    paymentEntity.getString("id");

            String razorpaySignatureHeader =
                    razorpaySignature;

            // Call secure verification service
            paymentService.verifyAndMarkPaymentSuccess(
                    null, // orderId not needed here
                    razorpayOrderId,
                    razorpayPaymentId,
                    razorpaySignatureHeader
            );
        }

        return "Webhook processed successfully";
    }

    // =====================================
    // 🔁 Read request body manually
    // =====================================
    private String getBody(HttpServletRequest request)
            throws IOException {

        StringBuilder stringBuilder = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }

        return stringBuilder.toString();
    }
}