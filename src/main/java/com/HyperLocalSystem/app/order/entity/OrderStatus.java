package com.HyperLocalSystem.app.order.entity;

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
    CANCELLED
}
