package com.charlesSchwab.account_service.exception;

import lombok.Getter;

@Getter
public class InvalidTransactionTypeException extends RuntimeException {
    private final String value;

    public InvalidTransactionTypeException(String value) {
        super("Invalid transaction type: " + value);
        this.value = value;
    }

}

