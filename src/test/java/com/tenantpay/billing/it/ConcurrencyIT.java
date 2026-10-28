package com.tenantpay.billing.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reproduces the race between a cancellation and a payment webhook. Another transaction cancels the subscription and
 * keeps its row lock while the inbox worker handles PAYMENT_CONFIRMED. The worker must wait for that commit and then
 * see CANCELED; it must not overwrite the row with ACTIVE (which a plain read followed by a merge would do).
 */
class ConcurrencyIT extends IntegrationTestBase {
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void paymentRacingACancellationDoesNotResurrectTheSubscription() throws Exception {
        Account account = signup("Race Co");
        JsonNode subscription = newSubscription(account);
        UUID subscriptionId = UUID.fromString(subscription.get("id").asText());
        String eventId = newEventId();

        CountDownLatch cancelHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseCancel = new CountDownLatch(1);
        CompletableFuture<Void> cancel = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    jdbc.queryForObject("SELECT id FROM subscriptions WHERE id = ? FOR UPDATE", UUID.class,
                            subscriptionId);
                    jdbc.update("UPDATE subscriptions SET status = 'CANCELED', canceled_at = now() WHERE id = ?",
                            subscriptionId);
                    cancelHoldsLock.countDown();
                    try {
                        releaseCancel.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
        assertThat(cancelHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(postWebhook(eventId, "PAYMENT_CONFIRMED", subscription).status()).isEqualTo(200);
        // Give the worker time to claim the event and reach the subscription while the cancel is uncommitted.
        await().pollDelay(Duration.ofMillis(800)).atMost(Duration.ofSeconds(5))
                .until(() -> !"RECEIVED".equals(inboxStatus(eventId)));
        releaseCancel.countDown();
        cancel.get(20, TimeUnit.SECONDS);

        awaitInboxStatus(eventId, "PROCESSED");
        String status = jdbc.queryForObject("SELECT status FROM subscriptions WHERE id = ?", String.class,
                subscriptionId);
        assertThat(status).isEqualTo("CANCELED");
        assertThat(get("/api/v1/subscriptions/" + subscriptionId, account.token()).text("status"))
                .isEqualTo("CANCELED");
    }
}
