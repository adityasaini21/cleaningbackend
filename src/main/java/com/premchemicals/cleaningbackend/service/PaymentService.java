package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.model.Order;
import com.premchemicals.cleaningbackend.model.PaymentTransaction;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import com.premchemicals.cleaningbackend.repository.PaymentTransactionRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;
import java.util.List;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${razorpay.key.id:}")
    private String keyId;

    @Value("${razorpay.key.secret:}")
    private String keySecret;

    @Value("${payment.dev-mode:false}")
    private boolean devMode;

    @Value("${phonepe.merchant.id:PGTESTPAYUAT86}")
    private String phonepeMerchantId;

    @Value("${phonepe.salt.key:96434309-7796-489d-8924-ab56988a6076}")
    private String phonepeSaltKey;

    @Value("${phonepe.salt.index:1}")
    private String phonepeSaltIndex;

    @Value("${phonepe.api.url:https://api-preprod.phonepe.com/apis/pg-sandbox}")
    private String phonepeApiUrl;

    // =========================================================
    // ✅ CREATE RAZORPAY ORDER
    // =========================================================


    @Transactional

    public String createRazorpayOrder(Long orderId) throws RazorpayException {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new UnsupportedOperationException(
                    "Razorpay integration is disabled.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Payment already processed");
        }

        if (order.getRazorpayOrderId() != null) {
            return order.getRazorpayOrderId();
        }

        RazorpayClient razorpayClient =
                new RazorpayClient(keyId, keySecret);

        JSONObject options = new JSONObject();

        options.put("amount", (int) (order.getTotalAmount() * 100));
        options.put("currency", "INR");
        options.put("receipt", "order_rcptid_" + order.getId());

        com.razorpay.Order razorpayOrder =
                razorpayClient.orders.create(options);

        String razorpayOrderId =
                razorpayOrder.get("id").toString();

        order.setRazorpayOrderId(razorpayOrderId);

        PaymentTransaction transaction =
                PaymentTransaction.builder()
                        .order(order)
                        .razorpayOrderId(razorpayOrderId)
                        .paymentStatus(PaymentStatus.PENDING)
                        .transactionTime(LocalDateTime.now())
                        .build();

        paymentTransactionRepository.save(transaction);

        return razorpayOrderId;
    }

    // =========================================================
    // ✅ VERIFY PAYMENT FROM FRONTEND
    // =========================================================
    @Transactional
    public void verifyAndMarkPaymentSuccess(
            Long orderId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) throws RazorpayException {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new UnsupportedOperationException(
                    "Razorpay integration is disabled.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getRazorpayOrderId().equals(razorpayOrderId)) {
            throw new RuntimeException("Razorpay Order ID mismatch");
        }

        // 🔐 Verify payment signature
        if (!devMode) {

            JSONObject options = new JSONObject();

            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            boolean isValid =
                    Utils.verifyPaymentSignature(options, keySecret);

            if (!isValid) {
                throw new RuntimeException("Invalid payment signature");
            }
        }

        markSuccessInternal(order, razorpayPaymentId);
    }

    // =========================================================
    // ✅ HANDLE WEBHOOK SUCCESS
    // =========================================================
    @Transactional
    public void handleWebhookSuccess(
            String razorpayOrderId,
            String razorpayPaymentId
    ) {

        Order order = orderRepository
                .findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Prevent duplicate processing
        if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            return;
        }

        markSuccessInternal(order, razorpayPaymentId);
    }

    // =========================================================
    // 🔥 INTERNAL SUCCESS HANDLER
    // =========================================================
    private void markSuccessInternal(
            Order order,
            String razorpayPaymentId
    ) {

        // Reduce stock safely
        orderService.markPaymentSuccess(order.getId());

        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setOrderStatus(OrderStatus.CONFIRMED);

        PaymentTransaction transaction =
                paymentTransactionRepository
                        .findByRazorpayOrderId(order.getRazorpayOrderId())
                        .orElseThrow(() ->
                                new RuntimeException("Transaction not found"));

        transaction.setRazorpayPaymentId(razorpayPaymentId);
        transaction.setPaymentStatus(PaymentStatus.COMPLETED);
        transaction.setTransactionTime(LocalDateTime.now());

        // Send notifications upon successful online payment confirmation
        User user = order.getUser();
        notificationService.createNotification(
                user,
                "Order Placed",
                "Your order #" + order.getId() + " has been placed successfully."
        );

        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                .toList();

        for (User admin : admins) {
            notificationService.createNotification(
                    admin,
                    "New Order Received",
                    "New order #" + order.getId() + " placed by " + user.getFullName()
            );
        }
    }

    // =========================================================
    // ❌ MARK PAYMENT FAILED
    // =========================================================
    @Transactional
    public void markPaymentFailed(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setOrderStatus(OrderStatus.CANCELLED);

        PaymentTransaction transaction =
                paymentTransactionRepository
                        .findByRazorpayOrderId(order.getRazorpayOrderId())
                        .orElseThrow(() ->
                                new RuntimeException("Transaction not found"));

        transaction.setPaymentStatus(PaymentStatus.FAILED);
        transaction.setTransactionTime(LocalDateTime.now());
    }

    // =========================================================
    // ✅ INITIATE PHONEPE PAYMENT
    // =========================================================
    @Transactional
    public String initiatePhonePePayment(Long orderId, String backendBaseUrl) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new RuntimeException("Payment already processed");
        }

        String merchantTransactionId = "TXN_ORDER_" + order.getId() + "_" + System.currentTimeMillis();
        long amountInPaise = (long) (order.getTotalAmount() * 100);

        String callbackUrl = backendBaseUrl + "/api/payments/phonepe/callback/" + order.getId();
        if (backendBaseUrl.contains("localhost") || backendBaseUrl.contains("192.168.") || backendBaseUrl.contains("10.")) {
            callbackUrl = "https://webhook.site/cb975c69-2a7e-407f-8e42-1678dc2f9976";
        }
        String redirectUrl = backendBaseUrl + "/api/payments/phonepe/callback/" + order.getId();

        // Create Payload JSON
        String payloadJson = String.format(
            "{\"merchantId\":\"%s\",\"merchantTransactionId\":\"%s\",\"merchantUserId\":\"USER_%d\"," +
            "\"amount\":%d,\"redirectUrl\":\"%s\",\"redirectMode\":\"GET\",\"callbackUrl\":\"%s\"," +
            "\"paymentInstrument\":{\"type\":\"PAY_PAGE\"}}",
            phonepeMerchantId, merchantTransactionId, order.getUser().getId(), amountInPaise, redirectUrl, callbackUrl
        );

        String base64Payload = Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String verifyHeaderInput = base64Payload + "/pg/v1/pay" + phonepeSaltKey;
        String sha256Hex = calculateSha256Hex(verifyHeaderInput);
        String xVerify = sha256Hex + "###" + phonepeSaltIndex;

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            String requestBody = String.format("{\"request\":\"%s\"}", base64Payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(phonepeApiUrl + "/pg/v1/pay"))
                    .header("Content-Type", "application/json")
                    .header("X-VERIFY", xVerify)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject responseJson = new JSONObject(response.body());
                if (responseJson.getBoolean("success")) {
                    JSONObject data = responseJson.getJSONObject("data");
                    String redirectPayUrl = data.getJSONObject("instrumentResponse")
                            .getJSONObject("redirectInfo")
                            .getString("url");

                    // Set PhonePe txn ID in database
                    order.setRazorpayOrderId("PHONEPE_" + merchantTransactionId);
                    orderRepository.save(order);

                    PaymentTransaction transaction = PaymentTransaction.builder()
                            .order(order)
                            .razorpayOrderId("PHONEPE_" + merchantTransactionId)
                            .paymentStatus(PaymentStatus.PENDING)
                            .transactionTime(LocalDateTime.now())
                            .build();
                    paymentTransactionRepository.save(transaction);

                    return redirectPayUrl;
                }
            }
            throw new RuntimeException("PhonePe payment initiation failed: " + response.body());
        } catch (Exception e) {
            throw new RuntimeException("Error initiating PhonePe payment: " + e.getMessage(), e);
        }
    }

    // =========================================================
    // ✅ VERIFY PHONEPE PAYMENT
    // =========================================================
    @Transactional
    public boolean verifyPhonePePayment(Long orderId, String merchantTransactionId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        String txnId = merchantTransactionId;
        if (txnId == null || txnId.trim().isEmpty()) {
            String dbPayId = order.getRazorpayOrderId();
            if (dbPayId != null && dbPayId.startsWith("PHONEPE_")) {
                txnId = dbPayId.substring("PHONEPE_".length());
            } else {
                throw new RuntimeException("Merchant transaction ID not found in database for order: " + orderId);
            }
        }

        String verifyHeaderInput = "/pg/v1/status/" + phonepeMerchantId + "/" + txnId + phonepeSaltKey;
        String sha256Hex = calculateSha256Hex(verifyHeaderInput);
        String xVerify = sha256Hex + "###" + phonepeSaltIndex;

        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(phonepeApiUrl + "/pg/v1/status/" + phonepeMerchantId + "/" + txnId))
                    .header("Content-Type", "application/json")
                    .header("X-VERIFY", xVerify)
                    .header("X-MERCHANT-ID", phonepeMerchantId)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject responseJson = new JSONObject(response.body());
                if (responseJson.getBoolean("success") && "PAYMENT_SUCCESS".equals(responseJson.getString("code"))) {
                    JSONObject data = responseJson.getJSONObject("data");
                    String state = data.getString("state");
                    if ("COMPLETED".equals(state)) {
                        String transactionId = data.getString("transactionId");
                        markSuccessInternal(order, "PHONEPE_PAY_" + transactionId);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("PhonePe status check error: " + e.getMessage());
            return false;
        }
    }

    private String calculateSha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }
}