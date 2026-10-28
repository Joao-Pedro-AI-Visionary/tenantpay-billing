package com.tenantpay.billing.infrastructure.webhook;

import com.tenantpay.billing.application.ProcessWebhookEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Drives {@link ProcessWebhookEvents} on a fixed delay. Disabled with {@code tenantpay.scheduling.enabled=false}. */
public class WebhookPoller {
    private static final Logger log = LoggerFactory.getLogger(WebhookPoller.class);

    private final ProcessWebhookEvents processor;

    public WebhookPoller(ProcessWebhookEvents processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${tenantpay.webhooks.poll-interval:PT0.5S}")
    public void poll() {
        try {
            int claimed;
            // Drain bursts quickly instead of waiting one interval per batch.
            do {
                claimed = processor.processDue();
                if (claimed > 0) {
                    log.debug("Processed {} webhook event(s)", claimed);
                }
            } while (claimed > 0 && !Thread.currentThread().isInterrupted());
        } catch (RuntimeException e) {
            log.warn("Webhook polling failed, will retry on next tick: {}", e.toString());
        }
    }
}
