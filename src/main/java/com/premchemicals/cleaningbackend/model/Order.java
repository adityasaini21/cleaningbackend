package com.premchemicals.cleaningbackend.model;

import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🕒 Order creation timestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime orderDate;

    // 🔥 FIX: map to DB column "created_at"
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 💰 Total amount
    @Column(nullable = false)
    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String pincode;

    @Builder.Default
    @Column
    private Double deliveryCharge = 0.0;

    @Column
    private String deliveryBoyName;

    @Column
    private String deliveryBoyPhone;

    @Column(unique = true)
    private String razorpayOrderId;

    @Column
    private LocalDateTime deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> orderItems;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<PaymentTransaction> paymentTransactions;

    // 🔥 AUTO SET TIMESTAMP
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.orderDate = now;
    }
}