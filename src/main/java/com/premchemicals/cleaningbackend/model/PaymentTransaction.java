package com.premchemicals.cleaningbackend.model;

import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Many transactions can belong to one order
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Razorpay Order ID (Generated when creating Razorpay order)
    @Column(nullable = false, unique = true)
    private String razorpayOrderId;

    // Razorpay Payment ID (Generated after payment success)
    @Column(unique = true)
    private String razorpayPaymentId;

    // Razorpay Signature (For verification)
    private String razorpaySignature;

    // Payment Status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    // Card / UPI / Netbanking etc
    private String paymentMethod;

    // Transaction timestamp
    @Column(nullable = false)
    private LocalDateTime transactionTime;

    // 🔁 Auto-set time before persist
    @PrePersist
    public void prePersist() {
        this.transactionTime = LocalDateTime.now();
    }
}