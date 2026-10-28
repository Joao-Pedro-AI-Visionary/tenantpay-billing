package com.tenantpay.billing.application.port;

import java.util.function.Supplier;

/** Runs a block of work atomically. A runtime exception thrown by the block rolls everything back. */
public interface UnitOfWork {
    <T> T inTransaction(Supplier<T> work);

    default void run(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
