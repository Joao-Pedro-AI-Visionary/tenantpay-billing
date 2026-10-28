package com.tenantpay.billing.infrastructure.gateway;

import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.domain.Money;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAsaasGatewayTest {
    private final FakeAsaasGateway gateway = new FakeAsaasGateway();

    @Test
    void customerIdIsDerivedFromEmailAndDocument() {
        PaymentGateway.NewCustomer jane = new PaymentGateway.NewCustomer("Jane", "jane@example.test",
                "24971563792", "ref-1");
        String expected = "cus_fake_" + UUID.nameUUIDFromBytes(
                "jane@example.test24971563792".getBytes(StandardCharsets.UTF_8));

        assertThat(gateway.createCustomer(jane)).isEqualTo(expected);
        assertThat(gateway.createCustomer(new PaymentGateway.NewCustomer("Other name", "jane@example.test",
                "24971563792", "ref-2"))).isEqualTo(expected);
        assertThat(gateway.createCustomer(new PaymentGateway.NewCustomer("Jane", "jane@example.test",
                "11222333000181", "ref-1"))).isNotEqualTo(expected);
    }

    @Test
    void paymentIdIsDerivedFromExternalReference() {
        PaymentGateway.NewCharge charge = new PaymentGateway.NewCharge("cus", Money.brl(4990),
                LocalDate.parse("2026-10-31"), "x", "charge-1");

        String id = gateway.createCharge(charge);

        assertThat(id).isEqualTo("pay_fake_" + UUID.nameUUIDFromBytes("charge-1".getBytes(StandardCharsets.UTF_8)));
        assertThat(gateway.createCharge(charge)).isEqualTo(id);
        assertThat(gateway.createCharge(new PaymentGateway.NewCharge("cus", Money.brl(4990),
                LocalDate.parse("2026-10-31"), "x", "charge-2"))).isNotEqualTo(id);
        assertThat(gateway.name()).isEqualTo("fake");
        gateway.cancelCharge(id);
    }
}
