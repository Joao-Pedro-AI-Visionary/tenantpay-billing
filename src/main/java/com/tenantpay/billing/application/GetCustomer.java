package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Customer;

import java.util.UUID;

public class GetCustomer {
    private final CustomerRepository customers;
    private final UnitOfWork uow;

    public GetCustomer(CustomerRepository customers, UnitOfWork uow) {
        this.customers = customers;
        this.uow = uow;
    }

    public Customer execute(UUID tenantId, UUID customerId) {
        return uow.inTransaction(() -> customers.findById(tenantId, customerId)
                .orElseThrow(() -> NotFoundException.of("Customer", customerId)));
    }
}
