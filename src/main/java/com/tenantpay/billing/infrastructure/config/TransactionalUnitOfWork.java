package com.tenantpay.billing.infrastructure.config;

import com.tenantpay.billing.application.port.UnitOfWork;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** {@link UnitOfWork} backed by Spring's programmatic transactions (REQUIRED propagation). */
public class TransactionalUnitOfWork implements UnitOfWork {
    private final TransactionTemplate template;

    public TransactionalUnitOfWork(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
