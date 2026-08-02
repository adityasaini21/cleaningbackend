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

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public String handleWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature
    ) throws Exception {

        if (webhookSecret.isBlank()) {
            return "Razorpay webhook disabled";
        }

        String payload = getBody(request);

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

        if ("payment.captured".equals(event)) {

            JSONObject paymentEntity =
                    jsonObject.getJSONObject("payload")
                            .getJSONObject("payment")
                            .getJSONObject("entity");

            String razorpayOrderId =
                    paymentEntity.getString("order_id");

            String razorpayPaymentId =
                    paymentEntity.getString("id");

            paymentService.verifyAndMarkPaymentSuccess(
                    null,
                    razorpayOrderId,
                    razorpayPaymentId,
                    razorpaySignature
            );
        }

        return "Webhook processed successfully";
    }

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