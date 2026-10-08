package com.HyperLocalSystem.app.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Values of {@code unified_orders.status}. The blueprint defines PENDING, BATCHED, PICKING and
 * COMPLETED; the others cover payment, hand-over and cancellation.
 */
public enum OrderStatus {
    /** Created, waiting for the UPI hold. */
    PENDING,
    /** Payment held; kitchen ticket sent and Kirana broadcast running in parallel. */
    CONFIRMED,
    /** Injected into another rider's route by the co-linear batching engine. */
    BATCHED,
    /** Rider is collecting from the Kirana / restaurant stops. */
    PICKING,
    OUT_FOR_DELIVERY,
    COMPLETED,
    CANCELLED;

    /** Statuses in which the customer still has an order in flight. */
    public static final Set<OrderStatus> ACTIVE = EnumSet.of(PENDING, CONFIRMED, BATCHED, PICKING, OUT_FOR_DELIVERY);

    /** The only legal moves; {@link UnifiedOrder#moveTo} rejects everything else. */
    public boolean canMoveTo(OrderStatus next) {
        Set<OrderStatus> allowed = switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PICKING, BATCHED, CANCELLED);
            case BATCHED -> EnumSet.of(PICKING, OUT_FOR_DELIVERY, CANCELLED);
            case PICKING -> EnumSet.of(OUT_FOR_DELIVERY, CANCELLED);
            case OUT_FOR_DELIVERY -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
        return allowed.contains(next);
    }
}
