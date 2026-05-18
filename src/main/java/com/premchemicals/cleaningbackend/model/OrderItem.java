package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    private double price;

    // 🔗 Many items belong to one order
    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    // 🔗 Many items refer to one product
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
}