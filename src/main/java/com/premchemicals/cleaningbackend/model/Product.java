package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🏷 Product Name
    @Column(nullable = false)
    private String name;

    // 📝 Product Description
    @Column(length = 2000)
    private String description;

    // 🖼 Product Image URL
    @Column(name = "image_url")
    private String imageUrl;

    // 💰 Selling Price
    @Column(nullable = false)
    private Double price;

    // 🏭 Manufacturing Cost Price (INTERNAL ONLY)
    @Column(nullable = false)
    private Double costPrice;

    // 📦 Available Stock
    @Column(nullable = false)
    private Integer stock;

    // ✅ Soft delete flag
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
}