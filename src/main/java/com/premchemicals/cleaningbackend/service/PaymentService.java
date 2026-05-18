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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final OrderService orderService;

    // ✅ FIXED PROPERTY NAMES
    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${payment.dev-mode:false}")
    private boolean devMode;

    // =========================================================
    // ✅ CREATE RAZORPAY ORDER
    // =========================================================
    @Transactional
    public String createRazorpayOrder(Long orderId) throws RazorpayException {

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
}