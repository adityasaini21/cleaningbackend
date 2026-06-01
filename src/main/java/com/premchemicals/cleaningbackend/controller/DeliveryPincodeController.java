package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.DeliveryPincodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pincode")
@RequiredArgsConstructor
public class DeliveryPincodeController {

    private final DeliveryPincodeService service;

    @GetMapping("/check/{pincode}")
    public Map<String, Boolean> checkPincode(
            @PathVariable String pincode
    ) {

        boolean deliverable =
                service.isDeliverable(pincode);

        return Map.of(
                "deliverable",
                deliverable
        );
    }
}