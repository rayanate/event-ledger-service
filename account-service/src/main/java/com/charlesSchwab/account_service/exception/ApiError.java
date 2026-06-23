package com.charlesSchwab.account_service.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String error, Object detail) {
    public static ApiError of(HttpStatus s, String error, Object detail) {
        return new ApiError(Instant.now(), s.value(), error, detail);
    }
}
