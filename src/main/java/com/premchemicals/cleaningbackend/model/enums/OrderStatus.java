package com.premchemicals.cleaningbackend.model.enums;

public enum OrderStatus {

    CREATED,            // Order placed
    CONFIRMED,          // Payment confirmed / COD accepted
    OUT_FOR_DELIVERY,   // Delivery boy dispatched
    DELIVERED,          // Order delivered successfully
    CANCELLED           // Order cancelled

}