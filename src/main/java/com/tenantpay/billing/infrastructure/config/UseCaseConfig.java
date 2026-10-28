package com.tenantpay.billing.infrastructure.config;

import com.tenantpay.billing.application.CancelSubscription;
import com.tenantpay.billing.application.CreateCustomer;
import com.tenantpay.billing.application.CreatePlan;
import com.tenantpay.billing.application.DeactivatePlan;
import com.tenantpay.billing.application.GetCustomer;
import com.tenantpay.billing.application.GetDashboard;
import com.tenantpay.billing.application.GetMe;
import com.tenantpay.billing.application.GetPlan;
import com.tenantpay.billing.application.GetSubscription;
import com.tenantpay.billing.application.IngestWebhook;
import com.tenantpay.billing.application.ListCharges;
import com.tenantpay.billing.application.ListCustomers;
import com.tenantpay.billing.application.ListPlans;
import com.tenantpay.billing.application.ListSubscriptions;
import com.tenantpay.billing.application.ListWebhookEvents;
import com.tenantpay.billing.application.Login;
import com.tenantpay.billing.application.ProcessWebhookEvents;
import com.tenantpay.billing.application.ReprocessWebhookEvent;
import com.tenantpay.billing.application.SignUpTenant;
import com.tenantpay.billing.application.SimulatePaymentWebhook;
import com.tenantpay.billing.application.Subscribe;
import com.tenantpay.billing.application.UpdatePlan;
import com.tenantpay.billing.application.port.ChargeRepository;
import com.tenantpay.billing.application.port.CustomerRepository;
import com.tenantpay.billing.application.port.PasswordHasher;
import com.tenantpay.billing.application.port.PaymentGateway;
import com.tenantpay.billing.application.port.PlanRepository;
import com.tenantpay.billing.application.port.SubscriptionRepository;
import com.tenantpay.billing.application.port.TenantRepository;
import com.tenantpay.billing.application.port.TokenIssuer;
import com.tenantpay.billing.application.port.UnitOfWork;
import com.tenantpay.billing.application.port.UserRepository;
import com.tenantpay.billing.application.port.WebhookEventRepository;
import com.tenantpay.billing.domain.RetryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Wires the framework free use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantpayProperties.class)
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new TransactionalUnitOfWork(new TransactionTemplate(transactionManager));
    }

    @Bean
    RetryPolicy webhookRetryPolicy(TenantpayProperties properties) {
        TenantpayProperties.Webhooks webhooks = properties.webhooks();
        return new RetryPolicy(webhooks.maxAttempts(), webhooks.baseBackoff(), webhooks.maxBackoff());
    }

    @Bean
    SignUpTenant signUpTenant(TenantRepository tenants, UserRepository users, PasswordHasher hasher,
                              TokenIssuer tokens, UnitOfWork uow, Clock clock) {
        return new SignUpTenant(tenants, users, hasher, tokens, uow, clock);
    }

    @Bean
    Login login(UserRepository users, PasswordHasher hasher, TokenIssuer tokens) {
        return new Login(users, hasher, tokens);
    }

    @Bean
    GetMe getMe(UserRepository users, TenantRepository tenants, UnitOfWork uow) {
        return new GetMe(users, tenants, uow);
    }

    @Bean
    CreatePlan createPlan(PlanRepository plans, UnitOfWork uow, Clock clock) {
        return new CreatePlan(plans, uow, clock);
    }

    @Bean
    UpdatePlan updatePlan(PlanRepository plans, UnitOfWork uow) {
        return new UpdatePlan(plans, uow);
    }

    @Bean
    DeactivatePlan deactivatePlan(PlanRepository plans, UnitOfWork uow) {
        return new DeactivatePlan(plans, uow);
    }

    @Bean
    ListPlans listPlans(PlanRepository plans, UnitOfWork uow) {
        return new ListPlans(plans, uow);
    }

    @Bean
    GetPlan getPlan(PlanRepository plans, UnitOfWork uow) {
        return new GetPlan(plans, uow);
    }

    @Bean
    CreateCustomer createCustomer(CustomerRepository customers, PaymentGateway gateway, UnitOfWork uow,
                                  Clock clock) {
        return new CreateCustomer(customers, gateway, uow, clock);
    }

    @Bean
    ListCustomers listCustomers(CustomerRepository customers, UnitOfWork uow) {
        return new ListCustomers(customers, uow);
    }

    @Bean
    GetCustomer getCustomer(CustomerRepository customers, UnitOfWork uow) {
        return new GetCustomer(customers, uow);
    }

    @Bean
    Subscribe subscribe(CustomerRepository customers, PlanRepository plans, SubscriptionRepository subscriptions,
                        ChargeRepository charges, PaymentGateway gateway, UnitOfWork uow, Clock clock) {
        return new Subscribe(customers, plans, subscriptions, charges, gateway, uow, clock);
    }

    @Bean
    CancelSubscription cancelSubscription(SubscriptionRepository subscriptions, ChargeRepository charges,
                                          CustomerRepository customers, PlanRepository plans,
                                          PaymentGateway gateway, UnitOfWork uow, Clock clock) {
        return new CancelSubscription(subscriptions, charges, customers, plans, gateway, uow, clock);
    }

    @Bean
    ListSubscriptions listSubscriptions(SubscriptionRepository subscriptions, CustomerRepository customers,
                                        PlanRepository plans, ChargeRepository charges, UnitOfWork uow) {
        return new ListSubscriptions(subscriptions, customers, plans, charges, uow);
    }

    @Bean
    GetSubscription getSubscription(SubscriptionRepository subscriptions, CustomerRepository customers,
                                    PlanRepository plans, ChargeRepository charges, UnitOfWork uow) {
        return new GetSubscription(subscriptions, customers, plans, charges, uow);
    }

    @Bean
    ListCharges listCharges(ChargeRepository charges, SubscriptionRepository subscriptions, UnitOfWork uow) {
        return new ListCharges(charges, subscriptions, uow);
    }

    @Bean
    GetDashboard getDashboard(PlanRepository plans, CustomerRepository customers,
                              SubscriptionRepository subscriptions, ChargeRepository charges, UnitOfWork uow) {
        return new GetDashboard(plans, customers, subscriptions, charges, uow);
    }

    @Bean
    IngestWebhook ingestWebhook(WebhookEventRepository events, ChargeRepository charges, UnitOfWork uow,
                                Clock clock) {
        return new IngestWebhook(events, charges, uow, clock);
    }

    @Bean
    ProcessWebhookEvents processWebhookEvents(WebhookEventRepository events, ChargeRepository charges,
                                              SubscriptionRepository subscriptions, UnitOfWork uow, Clock clock,
                                              RetryPolicy webhookRetryPolicy, TenantpayProperties properties) {
        return new ProcessWebhookEvents(events, charges, subscriptions, uow, clock, webhookRetryPolicy,
                properties.webhooks().batchSize(), properties.webhooks().processingLease());
    }

    @Bean
    ReprocessWebhookEvent reprocessWebhookEvent(WebhookEventRepository events, UnitOfWork uow) {
        return new ReprocessWebhookEvent(events, uow);
    }

    @Bean
    ListWebhookEvents listWebhookEvents(WebhookEventRepository events, UnitOfWork uow) {
        return new ListWebhookEvents(events, uow);
    }

    @Bean
    SimulatePaymentWebhook simulatePaymentWebhook(SubscriptionRepository subscriptions, ChargeRepository charges,
                                                  CustomerRepository customers, IngestWebhook ingest,
                                                  UnitOfWork uow, Clock clock) {
        return new SimulatePaymentWebhook(subscriptions, charges, customers, ingest, uow, clock);
    }
}
