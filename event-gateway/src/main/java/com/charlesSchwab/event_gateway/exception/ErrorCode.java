package com.charlesSchwab.event_gateway.exception;

public enum ErrorCode {
    VALIDATION_FAILED,
    INVALID_TRANSACTION_TYPE,
    MALFORMED_REQUEST,
    EVENT_NOT_FOUND,
    ACCOUNT_SERVICE_UNAVAILABLE,
    RATE_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
