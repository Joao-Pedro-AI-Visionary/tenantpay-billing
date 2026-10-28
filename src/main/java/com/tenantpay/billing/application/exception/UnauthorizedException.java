package com.tenantpay.billing.application.exception;

/** Credentials or shared secrets did not match. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
