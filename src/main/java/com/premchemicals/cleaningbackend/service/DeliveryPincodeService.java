package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.repository.DeliveryPincodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryPincodeService {

    private final DeliveryPincodeRepository repository;

    public boolean isDeliverable(String pincode) {
        if (pincode == null) return false;
        String cleanPincode = pincode.trim();
        
        // Dynamically allow all Kanpur Nagar (208xxx) postal zones
        if (cleanPincode.startsWith("208")) {
            return true;
        }

        return repository
                .findByPincodeAndActiveTrue(cleanPincode)
                .isPresent();
    }
}