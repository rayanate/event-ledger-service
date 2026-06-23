package com.charlesSchwab.account_service.exception;

import com.charlesSchwab.account_service.enums.TransactionType;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Arrays;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Bean validation failures: @NotNull, @DecimalMin, @NotBlank, etc. -> 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        var details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "validation_failed", details));
    }

    // Malformed JSON, or an unknown enum like type="TRANSFER" -> 400 (not 500)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        InvalidFormatException ife = findCause(e, InvalidFormatException.class);
        if (ife != null && ife.getTargetType() == TransactionType.class) {
            String allowed = Arrays.toString(TransactionType.values());
            return ResponseEntity.badRequest()
                    .body(ApiError.of(HttpStatus.BAD_REQUEST, "invalid_transaction_type",
                            "type must be one of " + allowed));
        }

        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST, "malformed_request", "Unreadable or invalid request body"));
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

    // Anything unexpected -> clean 500 instead of the Whitelabel page
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", e.getMessage()));
    }
}
