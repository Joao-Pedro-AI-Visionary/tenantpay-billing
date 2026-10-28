package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.Charge;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository {
    void save(Charge charge);

    /** Newest first. */
    List<Charge> findAll(UUID tenantId);

    /** Newest first. */
    List<Charge> findBySubscriptionIds(UUID tenantId, Collection<UUID> subscriptionIds);

    /**
     * System lookup used only by webhook ingestion and processing, where the tenant is not known yet. Gateway payment
     * ids are globally unique, and the caller binds the result to the owning tenant.
     */
    Optional<Charge> findByGatewayPaymentId(String gatewayPaymentId);

    /**
     * Same system lookup as {@link #findByGatewayPaymentId(String)} but returns only the owner keys, so the caller can
     * lock the subscription before it loads (and caches) the charge itself.
     */
    Optional<ChargeOwner> findOwnerByGatewayPaymentId(String gatewayPaymentId);

    /** Tenant and subscription that own a charge. */
    record ChargeOwner(UUID tenantId, UUID subscriptionId) {
    }

    /** Sum of CONFIRMED and RECEIVED charges. */
    long sumPaidAmountCents(UUID tenantId);
}
