package com.tenantpay.billing.application;

import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.domain.Customer;

import java.util.List;
import java.util.UUID;

public class ListCustomers {
    private final CustomerRepository customers;
    private final UnitOfWork uow;

    public ListCustomers(CustomerRepository customers, UnitOfWork uow) {
        this.customers = customers;
        this.uow = uow;
    }

    public List<Customer> execute(UUID tenantId) {
        return uow.inTransaction(() -> customers.findAll(tenantId));
    }
}
