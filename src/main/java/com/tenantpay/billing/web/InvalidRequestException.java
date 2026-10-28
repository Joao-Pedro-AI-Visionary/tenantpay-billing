package com.tenantpay.billing.web;

/** A request body that could not be interpreted (reported as 400). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
