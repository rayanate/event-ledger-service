package com.charlesSchwab.account_service.exception;

import com.charlesSchwab.account_service.enums.TransactionType;
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

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Bean validation failures: @NotNull, @DecimalMin, @NotBlank, etc. -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        var details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.warn("validation.failed fields={}", details);
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "validation_failed", details));
    }

    // Malformed JSON, or an unknown enum like type="TRANSFER" -> 400 (not 500)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        InvalidFormatException ife = findCause(e, InvalidFormatException.class);
        if (ife != null && ife.getTargetType() == TransactionType.class) {
            String allowed = Arrays.toString(TransactionType.values());
            log.warn("invalid_transaction_type value={} allowed={}", ife.getValue(), allowed);
            return ResponseEntity.badRequest()
                    .body(ApiError.of(HttpStatus.BAD_REQUEST, "invalid_transaction_type",
                            "type must be one of " + allowed));
        }
        log.warn("malformed_request error={}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "malformed_request", "Unreadable or invalid request body"));
    }

    // Anything unexpected -> clean 500 instead of the Whitelabel page
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        log.error("unhandled_exception error={}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", e.getMessage()));
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
