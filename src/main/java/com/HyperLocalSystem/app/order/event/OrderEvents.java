package com.HyperLocalSystem.app.order.event;

import com.HyperLocalSystem.app.order.entity.OrderStatus;

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

    /** The restaurant reported a kitchen delay: hold back the Kirana broadcast by {@code delay}. */
    public record KitchenDelayedEvent(UUID orderId, Duration delay) {
    }
}
