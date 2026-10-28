package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Customer;

import java.time.Clock;
import java.util.UUID;

/**
 * Registers the customer in the payment gateway first and stores it locally afterwards, so that no network call is
 * made while a database transaction is open.
 */
public class CreateCustomer {
    private final CustomerRepository customers;
    private final PaymentGateway gateway;
    private final UnitOfWork uow;
    private final Clock clock;

    public CreateCustomer(CustomerRepository customers, PaymentGateway gateway, UnitOfWork uow, Clock clock) {
        this.customers = customers;
        this.gateway = gateway;
        this.uow = uow;
        this.clock = clock;
    }

    public Customer execute(Command command) {
        UUID id = UUID.randomUUID();
        // Validate the input before talking to the gateway.
        Customer.create(id, command.tenantId(), command.name(), command.email(), command.document(), "pending",
                clock.instant());
        String gatewayId = gateway.createCustomer(new PaymentGateway.NewCustomer(command.name().trim(),
                command.email().trim(), command.document().trim(), id.toString()));
        Customer customer = Customer.create(id, command.tenantId(), command.name(), command.email(),
                command.document(), gatewayId, clock.instant());
        uow.run(() -> customers.save(customer));
        return customer;
    }

    public record Command(UUID tenantId, String name, String email, String document) {
    }
}
