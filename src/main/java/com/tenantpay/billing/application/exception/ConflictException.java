package com.tenantpay.billing.application.exception;

/** The request is valid but clashes with the current state (duplicate email, for example). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
