package com.deadlinekeeper.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.warn("[{}] {}", requestId, e.getMessage());
        return ResponseEntity.status(404).body(Map.of(
                "error", Map.of(
                        "code", "NOT_FOUND",
                        "message", e.getMessage(),
                        "resourceType", e.getResourceType(),
                        "resourceId", e.getResourceId()
                ),
                "requestId", requestId
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.warn("[{}] Access denied: {}", requestId, e.getMessage());
        return ResponseEntity.status(403).body(Map.of(
                "error", Map.of("code", "FORBIDDEN", "message", e.getMessage()),
                "requestId", requestId
        ));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.warn("[{}] Validation error: {}", requestId, e.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", Map.of("code", "VALIDATION_ERROR", "message", e.getMessage()));
        if (!e.getFieldErrors().isEmpty()) {
            body.put("fieldErrors", e.getFieldErrors());
        }
        body.put("requestId", requestId);
        return ResponseEntity.status(422).body(body);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, Object>> handleExternal(ExternalServiceException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] External service error ({}): {}", requestId, e.getService(), e.getMessage());
        return ResponseEntity.status(502).body(Map.of(
                "error", Map.of(
                        "code", "EXTERNAL_SERVICE_ERROR",
                        "service", e.getService(),
                        "message", e.getMessage()
                ),
                "requestId", requestId
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.warn("[{}] Unauthorized: {}", requestId, e.getMessage());
        return ResponseEntity.status(401).body(Map.of(
                "error", Map.of("code", "UNAUTHORIZED", "message", e.getMessage()),
                "requestId", requestId
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.warn("[{}] Data integrity violation: {}", requestId, e.getMessage());
        return ResponseEntity.status(409).body(Map.of(
                "error", Map.of("code", "CONFLICT", "message", "Resource already exists or constraint violation"),
                "requestId", requestId
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] Unexpected error: {}", requestId, e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of(
                "error", Map.of("code", "INTERNAL_ERROR", "message", "An unexpected error occurred"),
                "requestId", requestId
        ));
    }
}
