package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}