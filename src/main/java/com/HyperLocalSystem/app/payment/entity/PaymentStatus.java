package com.HyperLocalSystem.app.payment.entity;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of the UPI "hold then capture" payment (or an instant wallet payment). */
public enum PaymentStatus {
    /** Waiting for the customer to approve the UPI mandate. */
    HOLD_PENDING,
    /** Funds blocked; nothing captured yet. */
    HELD,
    /** Money taken (first merchant release, delivery, or a wallet payment). */
    CAPTURED,
    /** Hold dropped, e.g. order cancelled before capture. */
    RELEASED,
    /** Captured, then part of it went back to the customer's wallet (missing items). */
    PARTIALLY_REFUNDED,
    /** Captured, then all of it went back (cancelled wallet payment). */
    REFUNDED,
    FAILED;

    /** The only legal moves; {@link Payment#moveTo} rejects everything else. */
    public boolean canMoveTo(PaymentStatus next) {
        Set<PaymentStatus> allowed = switch (this) {
            case HOLD_PENDING -> EnumSet.of(HELD, FAILED, RELEASED);
            case HELD -> EnumSet.of(CAPTURED, RELEASED);
            case CAPTURED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            case PARTIALLY_REFUNDED -> EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            case FAILED -> EnumSet.of(HOLD_PENDING);
            case RELEASED, REFUNDED -> EnumSet.noneOf(PaymentStatus.class);
        };
        return allowed.contains(next);
    }
}
