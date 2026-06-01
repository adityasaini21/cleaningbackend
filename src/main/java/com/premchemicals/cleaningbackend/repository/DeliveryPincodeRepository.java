package com.premchemicals.cleaningbackend.repository;

import com.premchemicals.cleaningbackend.model.DeliveryPincode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryPincodeRepository
        extends JpaRepository<DeliveryPincode, Long> {

    Optional<DeliveryPincode>
    findByPincodeAndActiveTrue(String pincode);
}