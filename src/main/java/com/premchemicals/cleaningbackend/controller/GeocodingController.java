package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.service.GoogleMapsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/geocoding")
@RequiredArgsConstructor
public class GeocodingController {

    private static final Logger log = LoggerFactory.getLogger(GeocodingController.class);
    private final GoogleMapsService googleMapsService;

    @GetMapping("/reverse")
    public ResponseEntity<?> reverseGeocode(
            @RequestParam double latitude,
            @RequestParam double longitude
    ) {
        log.info("option 1 (geocoding api hit)");

        Map<String, Object> result = googleMapsService.reverseGeocode(latitude, longitude);

        if (result != null) {
            return ResponseEntity.ok(result);
        } else {
            log.warn("option 1 (geocoding api failed)");
            return ResponseEntity.status(503).body(Map.of("error", "Google Reverse Geocoding unavailable"));
        }
    }
}
