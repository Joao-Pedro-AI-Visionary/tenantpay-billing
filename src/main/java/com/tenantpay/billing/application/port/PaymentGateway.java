package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Money;

import java.time.LocalDate;

/** Outbound port to the payment provider. Implementations throw {@link GatewayException} on failure. */
public interface PaymentGateway {

    /** Short identifier shown to operators, for example "fake" or "asaas". */
    String name();

    /** Registers the payer and returns the gateway customer id. */
    String createCustomer(NewCustomer customer);

    /** Creates a payment request and returns the gateway payment id. */
    String createCharge(NewCharge charge);

    void cancelCharge(String gatewayPaymentId);

    record NewCustomer(String name, String email, String document, String externalReference) {
    }

    record NewCharge(String gatewayCustomerId, Money amount, LocalDate dueDate, String description,
                     String externalReference) {
    }
}
