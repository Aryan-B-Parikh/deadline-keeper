package com.deadlinekeeper.exception;

public class ExternalServiceException extends RuntimeException {
    private final String service;

    public ExternalServiceException(String service, String message) {
        super(message);
        this.service = service;
    }

    public ExternalServiceException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() { return service; }
}
