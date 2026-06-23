package com.charlesSchwab.event_gateway.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        var details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.warn("validation.failed fields={}", details);
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof InvalidFormatException ife) {
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                String allowed = Arrays.stream(ife.getTargetType().getEnumConstants())
                        .map(Object::toString).collect(Collectors.joining(", "));
                String bad = String.valueOf(ife.getValue());
                log.warn("invalid_transaction_type value={} allowed={}", bad, allowed);
                return ResponseEntity.badRequest().body(ApiError.of(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.INVALID_TRANSACTION_TYPE,
                        "Invalid transaction type '" + bad + "'. Allowed: " + allowed));
            }
        }
        log.warn("malformed_request error={}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(
                HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Unreadable or invalid request body"));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> onEventNotFound(EventNotFoundException e) {
        // warn already logged in EventService — no double-log here
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, ErrorCode.EVENT_NOT_FOUND, e.getMessage()));
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ApiError> onAccountServiceUnavailable(AccountServiceUnavailableException e) {
        log.error("account_service.unavailable error={}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE,
                        ErrorCode.ACCOUNT_SERVICE_UNAVAILABLE,
                        "Account Service is currently unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        log.error("unhandled_exception error={}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, e.getMessage()));
    }
}