package com.charlesSchwab.event_gateway.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

