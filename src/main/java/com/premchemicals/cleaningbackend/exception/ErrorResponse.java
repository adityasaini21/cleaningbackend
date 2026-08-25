package com.premchemicals.cleaningbackend.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private String correlationId;

    private Map<String, String> errors; // optional (for validation)

    public ErrorResponse(LocalDateTime timestamp,
                         int status,
                         String error,
                         String message,
                         String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(LocalDateTime timestamp,
                         int status,
                         String error,
                         String message,
                         String path,
                         String correlationId) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
    }

    public ErrorResponse(LocalDateTime timestamp,
                         int status,
                         String error,
                         String message,
                         String path,
                         Map<String, String> errors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.errors = errors;
    }
}