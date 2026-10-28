package com.tenantpay.billing.application.exception;

/** The resource does not exist or belongs to another tenant (both cases are reported the same way). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " " + id + " not found");
    }
}
