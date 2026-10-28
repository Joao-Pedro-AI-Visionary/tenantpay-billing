package com.tenantpay.billing.application.port;

import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository {

    boolean existsByEventId(String eventId);

    /**
     * Inserts the event unless another event with the same gateway event id already exists. Must be race safe:
     * returns false (instead of failing) when a concurrent request inserted the same id first.
     */
    boolean insertIfAbsent(WebhookEvent event);

    void save(WebhookEvent event);

    /** System lookup used by the background processor. */
    Optional<WebhookEvent> findById(UUID id);

    Optional<WebhookEvent> findById(UUID tenantId, UUID id);

    /** Newest first. {@code status} is optional (null means all). */
    List<WebhookEvent> findAll(UUID tenantId, WebhookEventStatus status);

    /**
     * Locks up to {@code limit} events that are due for processing (RECEIVED, FAILED with an elapsed retry time, or
     * PROCESSING with an expired lease), skipping rows locked by other workers. Must run inside a transaction.
     */
    List<WebhookEvent> lockDue(Instant now, int limit);
}
