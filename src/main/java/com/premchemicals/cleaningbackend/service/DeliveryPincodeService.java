package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.repository.DeliveryPincodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryPincodeService {

    private final DeliveryPincodeRepository repository;

    public boolean isDeliverable(String pincode) {

        return repository
                .findByPincodeAndActiveTrue(pincode)
                .isPresent();
    }
}