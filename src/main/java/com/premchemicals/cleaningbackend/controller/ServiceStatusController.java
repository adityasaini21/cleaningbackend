package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.ServiceStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ServiceStatusController {

    private final ServiceStatusService serviceStatusService;

    // Public endpoint for mobile app check
    @GetMapping("/api/service-status")
    public Map<String, Boolean> getServiceStatus() {
        return Map.of("suspended", serviceStatusService.isServiceSuspended());
    }

    // Admin endpoint to toggle service status
    @PostMapping("/admin/service-status")
    public Map<String, Boolean> updateServiceStatus(
            @RequestParam boolean suspended
    ) {
        serviceStatusService.setServiceSuspended(suspended);
        return Map.of("suspended", serviceStatusService.isServiceSuspended());
    }
}
