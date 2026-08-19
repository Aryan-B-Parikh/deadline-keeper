package com.deadlinekeeper.exception;

public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
    public AccessDeniedException() {
        super("Access denied");
    }
}
