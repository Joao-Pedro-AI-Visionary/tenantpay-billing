package com.tenantpay.billing.infrastructure.gateway;

import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.infrastructure.config.TenantpayProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Real Asaas adapter when {@code tenantpay.asaas.api-key} is set, offline fake otherwise. */
@Configuration(proxyBeanMethods = false)
public class GatewayConfig {

    @Bean
    @ConditionalOnExpression("!'${tenantpay.asaas.api-key:}'.isBlank()")
    PaymentGateway asaasGateway(RestClient.Builder builder, TenantpayProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return new AsaasGateway(builder.requestFactory(requestFactory), properties.asaas().baseUrl(),
                properties.asaas().apiKey());
    }

    @Bean
    @ConditionalOnExpression("'${tenantpay.asaas.api-key:}'.isBlank()")
    PaymentGateway fakeAsaasGateway() {
        return new FakeAsaasGateway();
    }
}
