package com.HyperLocalSystem.app.order.event;

import com.HyperLocalSystem.app.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

/**
 * Domain events of the order flow. They are published inside the business transaction and
 * consumed by {@code @TransactionalEventListener(AFTER_COMMIT) @Async} listeners, so listeners only
 * ever see committed data and callers never wait for dispatch/notifications. Swapping Spring
 * events for Kafka/RabbitMQ later only touches the publisher and the listeners, not the callers.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    /** Checkout created the order (payment may still be pending). */
    public record OrderPlacedEvent(UUID orderId, UUID customerId) {
    }

    /** Payment is secured (UPI hold confirmed or wallet paid): start the kitchen ticket, broadcast and dispatch. */
    public record OrderConfirmedEvent(UUID orderId) {
    }

    /** Cancelled by the customer or the system: stop broadcasts, offers and route stops. */
    public record OrderCancelledEvent(UUID orderId, UUID deliveryAgentId) {
    }

    /** Any change of the order's main status; drives the customer's live tracking push. */
    public record OrderStatusChangedEvent(UUID orderId, UUID customerId, OrderStatus status) {
    }

    /** A Kirana tapped "Items in Hand" first: add its pickup to the rider's route (or start dispatch). */
    public record KiranaClaimedEvent(UUID orderId, UUID merchantId) {
    }

    /**
     * The grocery part ended UNFULFILLED while the food carries on (the broadcast gave up, nothing was
     * available at the Kirana, or the drop happened without them). {@code refund} is what went back to
     * the wallet now: zero when an earlier path already refunded it.
     */
    public record GroceriesUnfulfilledEvent(UUID orderId, UUID customerId, BigDecimal refund, String reason) {
    }

    /** The restaurant reported a kitchen delay: hold back the Kirana broadcast by {@code delay}. */
    public record KitchenDelayedEvent(UUID orderId, Duration delay) {
    }
}
