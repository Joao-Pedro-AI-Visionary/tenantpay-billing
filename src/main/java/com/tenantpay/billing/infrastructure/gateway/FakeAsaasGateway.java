package com.tenantpay.billing.infrastructure.gateway;

import com.tenantpay.billing.application.port.PaymentGateway;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Offline gateway used by default (no API key configured). Ids are derived deterministically from the inputs, so the
 * same request always yields the same id and tests can predict them. No network access.
 */
public class FakeAsaasGateway implements PaymentGateway {

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String createCustomer(NewCustomer customer) {
        return "cus_fake_" + nameUuid(customer.email() + customer.document());
    }

    @Override
    public String createCharge(NewCharge charge) {
        return "pay_fake_" + nameUuid(charge.externalReference());
    }

    @Override
    public void cancelCharge(String gatewayPaymentId) {
        // nothing to do offline
    }

    private static String nameUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
