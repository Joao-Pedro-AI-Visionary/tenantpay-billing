package com.tenantpay.billing.application;

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
import com.tenantpay.billing.domain.Charge;
import com.tenantpay.billing.domain.ChargeStatus;
import com.tenantpay.billing.domain.Customer;
import com.tenantpay.billing.domain.Plan;
import com.tenantpay.billing.domain.Subscription;
import com.tenantpay.billing.domain.SubscriptionStatus;
import com.tenantpay.billing.domain.Tenant;
import com.tenantpay.billing.domain.User;
import com.tenantpay.billing.domain.WebhookEvent;
import com.tenantpay.billing.domain.WebhookEventStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Hand written in-memory implementations of the outbound ports. They keep the same object instances (no copies), so
 * the unit of work only counts transactions and does not roll back.
 */
final class InMemoryPorts {
    private InMemoryPorts() {
    }

    static final class CountingUnitOfWork implements UnitOfWork {
        int transactions;

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            transactions++;
            return work.get();
        }
    }

    static final class Tenants implements TenantRepository {
        final Map<UUID, Tenant> rows = new LinkedHashMap<>();

        @Override
        public void save(Tenant tenant) {
            rows.put(tenant.id(), tenant);
        }

        @Override
        public Optional<Tenant> findById(UUID tenantId) {
            return Optional.ofNullable(rows.get(tenantId));
        }
    }

    static final class Users implements UserRepository {
        final Map<UUID, User> rows = new LinkedHashMap<>();

        @Override
        public void save(User user) {
            rows.put(user.id(), user);
        }

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return findByEmail(normalizedEmail).isPresent();
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            return rows.values().stream().filter(u -> u.email().equals(normalizedEmail)).findFirst();
        }

        @Override
        public Optional<User> findById(UUID tenantId, UUID userId) {
            return Optional.ofNullable(rows.get(userId)).filter(u -> u.tenantId().equals(tenantId));
        }
    }

    static final class Plans implements PlanRepository {
        final Map<UUID, Plan> rows = new LinkedHashMap<>();

        @Override
        public void save(Plan plan) {
            rows.put(plan.id(), plan);
        }

        @Override
        public Optional<Plan> findById(UUID tenantId, UUID planId) {
            return Optional.ofNullable(rows.get(planId)).filter(p -> p.tenantId().equals(tenantId));
        }

        @Override
        public List<Plan> findAll(UUID tenantId) {
            return rows.values().stream().filter(p -> p.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<Plan> findAllByIds(UUID tenantId, Collection<UUID> planIds) {
            return findAll(tenantId).stream().filter(p -> planIds.contains(p.id())).toList();
        }

        @Override
        public long countActive(UUID tenantId) {
            return findAll(tenantId).stream().filter(Plan::active).count();
        }
    }

    static final class Customers implements CustomerRepository {
        final Map<UUID, Customer> rows = new LinkedHashMap<>();

        @Override
        public void save(Customer customer) {
            rows.put(customer.id(), customer);
        }

        @Override
        public Optional<Customer> findById(UUID tenantId, UUID customerId) {
            return Optional.ofNullable(rows.get(customerId)).filter(c -> c.tenantId().equals(tenantId));
        }

        @Override
        public List<Customer> findAll(UUID tenantId) {
            return rows.values().stream().filter(c -> c.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<Customer> findAllByIds(UUID tenantId, Collection<UUID> customerIds) {
            return findAll(tenantId).stream().filter(c -> customerIds.contains(c.id())).toList();
        }

        @Override
        public long count(UUID tenantId) {
            return findAll(tenantId).size();
        }
    }

    static final class Subscriptions implements SubscriptionRepository {
        final Map<UUID, Subscription> rows = new LinkedHashMap<>();

        @Override
        public void save(Subscription subscription) {
            rows.put(subscription.id(), subscription);
        }

        @Override
        public Optional<Subscription> findById(UUID tenantId, UUID subscriptionId) {
            return Optional.ofNullable(rows.get(subscriptionId)).filter(s -> s.tenantId().equals(tenantId));
        }

        /** Ids passed to {@link #findByIdForUpdate}, so tests can assert which paths take the row lock. */
        final List<UUID> locked = new ArrayList<>();

        @Override
        public Optional<Subscription> findByIdForUpdate(UUID tenantId, UUID subscriptionId) {
            locked.add(subscriptionId);
            return findById(tenantId, subscriptionId);
        }

        @Override
        public List<Subscription> findAll(UUID tenantId, SubscriptionStatus status) {
            return rows.values().stream()
                    .filter(s -> s.tenantId().equals(tenantId))
                    .filter(s -> status == null || s.status() == status)
                    .toList();
        }

        @Override
        public Map<SubscriptionStatus, Long> countByStatus(UUID tenantId) {
            Map<SubscriptionStatus, Long> counts = new EnumMap<>(SubscriptionStatus.class);
            findAll(tenantId, null).forEach(s -> counts.merge(s.status(), 1L, Long::sum));
            return counts;
        }
    }

    static final class Charges implements ChargeRepository {
        final Map<UUID, Charge> rows = new LinkedHashMap<>();

        @Override
        public void save(Charge charge) {
            rows.put(charge.id(), charge);
        }

        @Override
        public List<Charge> findAll(UUID tenantId) {
            return rows.values().stream().filter(c -> c.tenantId().equals(tenantId)).toList();
        }

        @Override
        public List<Charge> findBySubscriptionIds(UUID tenantId, Collection<UUID> subscriptionIds) {
            return findAll(tenantId).stream().filter(c -> subscriptionIds.contains(c.subscriptionId())).toList();
        }

        @Override
        public Optional<Charge> findByGatewayPaymentId(String gatewayPaymentId) {
            return rows.values().stream().filter(c -> c.gatewayPaymentId().equals(gatewayPaymentId)).findFirst();
        }

        @Override
        public Optional<ChargeOwner> findOwnerByGatewayPaymentId(String gatewayPaymentId) {
            return findByGatewayPaymentId(gatewayPaymentId).map(c -> new ChargeOwner(c.tenantId(), c.subscriptionId()));
        }

        @Override
        public long sumPaidAmountCents(UUID tenantId) {
            return findAll(tenantId).stream().filter(c -> c.status() == ChargeStatus.CONFIRMED
                    || c.status() == ChargeStatus.RECEIVED).mapToLong(Charge::amountCents).sum();
        }
    }

    static final class WebhookEvents implements WebhookEventRepository {
        final Map<UUID, WebhookEvent> rows = new LinkedHashMap<>();
        /** Simulates a concurrent insert that wins the race after the exists check. */
        boolean loseNextInsertRace;

        @Override
        public boolean existsByEventId(String eventId) {
            return rows.values().stream().anyMatch(e -> e.eventId().equals(eventId));
        }

        @Override
        public boolean insertIfAbsent(WebhookEvent event) {
            if (loseNextInsertRace) {
                loseNextInsertRace = false;
                return false;
            }
            if (existsByEventId(event.eventId())) {
                return false;
            }
            rows.put(event.id(), event);
            return true;
        }

        @Override
        public void save(WebhookEvent event) {
            rows.put(event.id(), event);
        }

        @Override
        public Optional<WebhookEvent> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<WebhookEvent> findById(UUID tenantId, UUID id) {
            return findById(id).filter(e -> tenantId.equals(e.tenantId()));
        }

        @Override
        public List<WebhookEvent> findAll(UUID tenantId, WebhookEventStatus status) {
            return rows.values().stream()
                    .filter(e -> tenantId.equals(e.tenantId()))
                    .filter(e -> status == null || e.status() == status)
                    .toList();
        }

        @Override
        public List<WebhookEvent> lockDue(Instant now, int limit) {
            return rows.values().stream()
                    .filter(e -> e.status() == WebhookEventStatus.RECEIVED
                            || ((e.status() == WebhookEventStatus.FAILED || e.status() == WebhookEventStatus.PROCESSING)
                            && !e.nextAttemptAt().isAfter(now)))
                    .sorted(Comparator.comparing(WebhookEvent::receivedAt))
                    .limit(limit)
                    .toList();
        }
    }

    /** Deterministic gateway double that records calls and can be told to fail. */
    static final class RecordingGateway implements PaymentGateway {
        final List<NewCustomer> customers = new ArrayList<>();
        final List<NewCharge> charges = new ArrayList<>();
        final List<String> canceled = new ArrayList<>();

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public String createCustomer(NewCustomer customer) {
            customers.add(customer);
            return "cus_" + customers.size();
        }

        @Override
        public String createCharge(NewCharge charge) {
            charges.add(charge);
            return "pay_" + charge.externalReference();
        }

        @Override
        public void cancelCharge(String gatewayPaymentId) {
            canceled.add(gatewayPaymentId);
        }
    }

    /** Reversible fake hash, good enough to test the use cases. */
    static final class PlainHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash.equals("hashed:" + rawPassword);
        }
    }

    static final class StubTokens implements TokenIssuer {
        @Override
        public IssuedToken issue(User user) {
            return new IssuedToken("token-for-" + user.id(), 3600);
        }
    }
}
