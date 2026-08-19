package com.deadlinekeeper.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of(
                "error", Map.of(
                        "code", "NOT_FOUND",
                        "message", e.getMessage(),
                        "resourceType", e.getResourceType(),
                        "resourceId", e.getResourceId()
                )
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of(
                "error", Map.of("code", "FORBIDDEN", "message", e.getMessage())
        ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException e) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of("code", "VALIDATION_ERROR", "message", e.getMessage()));
        if (!e.getFieldErrors().isEmpty()) {
            body.put("fieldErrors", e.getFieldErrors());
        }
        return ResponseEntity.status(422).body(body);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleExternal(ExternalServiceException e) {
        return ResponseEntity.status(502).body(Map.of(
                "error", Map.of(
                        "code", "EXTERNAL_SERVICE_ERROR",
                        "service", e.getService(),
                        "message", e.getMessage()
                )
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(401).body(Map.of(
                "error", Map.of("code", "UNAUTHORIZED", "message", e.getMessage())
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        return ResponseEntity.status(500).body(Map.of(
                "error", Map.of("code", "INTERNAL_ERROR", "message", "An unexpected error occurred")
        ));
    }
}
