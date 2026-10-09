package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.model.Order;
import com.premchemicals.cleaningbackend.model.PaymentTransaction;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentMethod;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Online payment is currently disabled. Please select Cash on Delivery (COD)."
        );
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        verifyOrderOwnership(order);

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
                "Your order #NUK" + order.getId() + " has been placed successfully."
        );

        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                .toList();

        for (User admin : admins) {
            notificationService.createNotification(
                    admin,
                    "New Order Received",
                    "New order #NUK" + order.getId() + " placed by " + user.getFullName()
            );
        }
    }

    // =========================================================
    // ❌ MARK PAYMENT FAILED
    // =========================================================
    @Transactional
    public void markPaymentFailed(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        verifyOrderOwnership(order);

        if (order.getPaymentMethod() == PaymentMethod.COD) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payment failure cannot be processed for Cash on Delivery orders. Use order cancellation instead."
            );
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setOrderStatus(OrderStatus.CANCELLED);

        if (order.getRazorpayOrderId() != null && !order.getRazorpayOrderId().isBlank()) {
            paymentTransactionRepository
                    .findByRazorpayOrderId(order.getRazorpayOrderId())
                    .ifPresent(transaction -> {
                        transaction.setPaymentStatus(PaymentStatus.FAILED);
                        transaction.setTransactionTime(LocalDateTime.now());
                    });
        }
    }

    private void verifyOrderOwnership(Order order) {
        if (order == null || order.getUser() == null || order.getUser().getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: Order ownership cannot be verified");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User is not authenticated");
        }

        String phoneNumber = auth.getName();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "User is not authenticated");
        }

        User loggedInUser = userRepository
                .findByPhoneNumber(phoneNumber)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        boolean isAdmin = loggedInUser.getRole() == Role.ROLE_ADMIN;

        if (!isAdmin && !order.getUser().getId().equals(loggedInUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied: You do not own this order");
        }
    }

    // =========================================================
    // ✅ INITIATE PHONEPE PAYMENT
    // =========================================================
    @Transactional
    public String initiatePhonePePayment(Long orderId, String backendBaseUrl) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Online payment is currently disabled. Please select Cash on Delivery (COD)."
        );
    }

    // =========================================================
    // ✅ VERIFY PHONEPE PAYMENT
    // =========================================================
    @Transactional
    public boolean verifyPhonePePayment(Long orderId, String merchantTransactionId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        verifyOrderOwnership(order);

        String dbPayId = order.getRazorpayOrderId();
        String expectedTxnId = (dbPayId != null && dbPayId.startsWith("PHONEPE_"))
                ? dbPayId.substring("PHONEPE_".length())
                : null;

        String txnId;
        if (merchantTransactionId != null && !merchantTransactionId.trim().isEmpty()) {
            if (expectedTxnId == null || !expectedTxnId.equals(merchantTransactionId.trim())) {
                throw new RuntimeException("Merchant transaction ID mismatch for order: " + orderId);
            }
            txnId = merchantTransactionId.trim();
        } else {
            if (expectedTxnId == null) {
                throw new RuntimeException("Merchant transaction ID not found in database for order: " + orderId);
            }
            txnId = expectedTxnId;
        }

        if (devMode) {
            System.out.println("⚠️ [DEV MODE] Simulating successful PhonePe payment for order: " + orderId);
            markSuccessInternal(order, "PHONEPE_MOCK_PAY_" + txnId);
            return true;
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
                        long paidAmountInPaise = data.getLong("amount");
                        long expectedAmountInPaise = Math.round(order.getTotalAmount() * 100);
                        
                        if (paidAmountInPaise != expectedAmountInPaise) {
                            throw new RuntimeException("Payment amount mismatch! Expected: " + expectedAmountInPaise + " paise, got: " + paidAmountInPaise + " paise.");
                        }

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