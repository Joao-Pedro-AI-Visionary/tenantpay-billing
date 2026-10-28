package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.domain.Customer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class CustomerRepositoryAdapter implements CustomerRepository {
    private final CustomerJpaRepository customers;

    CustomerRepositoryAdapter(CustomerJpaRepository customers) {
        this.customers = customers;
    }

    @Override
    public void save(Customer customer) {
        customers.save(CustomerEntity.from(customer));
    }

    @Override
    public Optional<Customer> findById(UUID tenantId, UUID customerId) {
        return customers.findByIdAndTenantId(customerId, tenantId).map(CustomerEntity::toDomain);
    }

    @Override
    public List<Customer> findAll(UUID tenantId) {
        return customers.findByTenantIdOrderByCreatedAtDescIdDesc(tenantId).stream()
                .map(CustomerEntity::toDomain).toList();
    }

    @Override
    public List<Customer> findAllByIds(UUID tenantId, Collection<UUID> customerIds) {
        return customers.findByTenantIdAndIdIn(tenantId, customerIds).stream().map(CustomerEntity::toDomain).toList();
    }

    @Override
    public long count(UUID tenantId) {
        return customers.countByTenantId(tenantId);
    }
}
