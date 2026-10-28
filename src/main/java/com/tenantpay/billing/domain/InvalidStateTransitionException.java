package com.tenantpay.billing.domain;

/** Raised when a state machine is asked to perform a transition it does not allow. */
public class InvalidStateTransitionException extends DomainException {
    public InvalidStateTransitionException(String aggregate, Object from, String action) {
        super(aggregate + " cannot " + action + " from status " + from);
    }
}
