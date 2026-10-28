package com.tenantpay.billing.application;

import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.port.TenantRepository;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.UserRepository;
import com.tenantpay.billing.domain.Role;
import com.tenantpay.billing.domain.Tenant;
import com.tenantpay.billing.domain.User;

import java.util.UUID;

/** Profile of the authenticated user. */
public class GetMe {
    private final UserRepository users;
    private final TenantRepository tenants;
    private final UnitOfWork uow;

    public GetMe(UserRepository users, TenantRepository tenants, UnitOfWork uow) {
        this.users = users;
        this.tenants = tenants;
        this.uow = uow;
    }

    public View execute(UUID tenantId, UUID userId) {
        return uow.inTransaction(() -> {
            User user = users.findById(tenantId, userId).orElseThrow(() -> NotFoundException.of("User", userId));
            Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> NotFoundException.of("Tenant", tenantId));
            return new View(user.id(), tenant.id(), tenant.name(), user.email(), user.role());
        });
    }

    public record View(UUID userId, UUID tenantId, String tenantName, String email, Role role) {
    }
}
