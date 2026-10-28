package com.tenantpay.billing.infrastructure.persistence;

import com.tenantpay.billing.domain.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantEntity() {
    }

    static TenantEntity from(Tenant tenant) {
        TenantEntity entity = new TenantEntity();
        entity.id = tenant.id();
        entity.name = tenant.name();
        entity.createdAt = tenant.createdAt();
        return entity;
    }

    Tenant toDomain() {
        return Tenant.restore(id, name, createdAt);
    }
}
