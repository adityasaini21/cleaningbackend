package com.premchemicals.cleaningbackend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    @PostMapping("/geocoding")
    public ResponseEntity<Map<String, String>> logGeocoding(@RequestBody Map<String, Object> payload) {
        String optionNumber = resolveOptionNumber(payload);

        log.info("Geocoding option selected: {}", optionNumber);

        return ResponseEntity.ok(Map.of("status", "logged"));
    }

    private String resolveOptionNumber(Map<String, Object> payload) {
        if (payload == null) {
            return "UNKNOWN";
        }
        Object numObj = payload.get("optionNumber");
        if (numObj != null) {
            String s = String.valueOf(numObj).trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        Object optObj = payload.get("option");
        if (optObj != null) {
            String s = String.valueOf(optObj);
            Matcher matcher = DIGIT_PATTERN.matcher(s);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "UNKNOWN";
    }
}
