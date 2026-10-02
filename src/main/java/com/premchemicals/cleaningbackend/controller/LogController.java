package com.premchemicals.cleaningbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    @PostMapping("/geocoding")
    public ResponseEntity<Map<String, String>> logGeocoding(@RequestBody Map<String, Object> payload) {
        String option = String.valueOf(payload.getOrDefault("option", "UNKNOWN"));
        String address = String.valueOf(payload.getOrDefault("address", ""));
        String landmark = String.valueOf(payload.getOrDefault("landmark", ""));
        String pincode = String.valueOf(payload.getOrDefault("pincode", ""));
        String city = String.valueOf(payload.getOrDefault("city", ""));
        String state = String.valueOf(payload.getOrDefault("state", ""));
        String full = String.valueOf(payload.getOrDefault("fullFormattedAddress", ""));
        Object lat = payload.get("latitude");
        Object lng = payload.get("longitude");

        log.info("==================================================");
        log.info("📍 [BACKEND GEOCODING TELEMETRY] {}", option);
        log.info("   • Coordinates: {}, {}", lat, lng);
        log.info("   • Address: {}", address);
        log.info("   • Landmark: {}", landmark);
        log.info("   • Pincode: {}", pincode);
        log.info("   • City: {}, State: {}", city, state);
        log.info("   • Full Address: {}", full);
        log.info("==================================================");

        return ResponseEntity.ok(Map.of("status", "logged"));
    }
}
