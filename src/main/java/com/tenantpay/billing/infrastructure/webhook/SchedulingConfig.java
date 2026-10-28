package com.tenantpay.billing.infrastructure.webhook;

import com.tenantpay.billing.application.ProcessWebhookEvents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "tenantpay.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    @Bean
    WebhookPoller webhookPoller(ProcessWebhookEvents processor) {
        return new WebhookPoller(processor);
    }
}
