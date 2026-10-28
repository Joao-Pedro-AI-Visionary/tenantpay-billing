package com.tenantpay.billing.application.port;

/** The payment gateway rejected the call or could not be reached. */
public class GatewayException extends RuntimeException {
    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
