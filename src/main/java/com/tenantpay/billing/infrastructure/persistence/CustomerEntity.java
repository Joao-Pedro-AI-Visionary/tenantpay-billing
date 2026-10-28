package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerEntity {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 14)
    private String document;
    @Column(name = "gateway_customer_id", nullable = false, length = 100)
    private String gatewayCustomerId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerEntity() {
    }

    static CustomerEntity from(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.id = customer.id();
        entity.tenantId = customer.tenantId();
        entity.name = customer.name();
        entity.email = customer.email();
        entity.document = customer.document();
        entity.gatewayCustomerId = customer.gatewayCustomerId();
        entity.createdAt = customer.createdAt();
        return entity;
    }

    Customer toDomain() {
        return Customer.restore(id, tenantId, name, email, document, gatewayCustomerId, createdAt);
    }
}
