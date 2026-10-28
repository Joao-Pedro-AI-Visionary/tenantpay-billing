package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Customer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    void save(Customer customer);

    Optional<Customer> findById(UUID tenantId, UUID customerId);

    /** Newest first. */
    List<Customer> findAll(UUID tenantId);

    List<Customer> findAllByIds(UUID tenantId, Collection<UUID> customerIds);

    long count(UUID tenantId);
}
