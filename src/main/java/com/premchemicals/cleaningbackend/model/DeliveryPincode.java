package com.premchemicals.cleaningbackend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_pincodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPincode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pincode;

    @Column(nullable = false)
    private String areaName;

    @Column(nullable = false)
    private boolean active = true;
}