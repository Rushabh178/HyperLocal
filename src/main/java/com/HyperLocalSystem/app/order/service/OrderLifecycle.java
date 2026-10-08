package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderCancelledEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderConfirmedEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderStatusChangedEvent;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that changes an order's main status. It applies the transition (validated by
 * {@link UnifiedOrder#moveTo}) and publishes the matching event, so tracking pushes and dispatch
 * never depend on every caller remembering to do it. Must run inside the caller's transaction.
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycle {

    private final ApplicationEventPublisher events;
    private final PaymentService paymentService;

    /** Payment is secured: the kitchen ticket and the Kirana broadcast start in parallel after commit. */
    @Transactional
    public void confirm(UnifiedOrder order) {
        order.moveTo(OrderStatus.CONFIRMED);
        if (order.hasFood()) {
            order.moveFoodTo(PartStatus.NEW);
        }
        if (order.hasGroceries()) {
            order.moveGroceryTo(PartStatus.BROADCASTING);
        }
        statusChanged(order);
        events.publishEvent(new OrderConfirmedEvent(order.getId()));
    }

    @Transactional
    public void moveTo(UnifiedOrder order, OrderStatus next) {
        order.moveTo(next);
        statusChanged(order);
    }

    /** Cancels both parts, gives the money back (release the hold or refund to the wallet) and stops dispatch. */
    @Transactional
    public void cancel(UnifiedOrder order, String reason) {
        order.moveTo(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        if (order.hasFood() && !order.getFoodStatus().isTerminal()) {
            order.moveFoodTo(PartStatus.CANCELLED);
        }
        if (order.hasGroceries() && !order.getGroceryStatus().isTerminal()) {
            order.moveGroceryTo(PartStatus.CANCELLED);
        }
        paymentService.releaseOrRefund(order.getId(), "Refund for cancelled order");
        statusChanged(order);
        events.publishEvent(new OrderCancelledEvent(order.getId(), order.getDeliveryAgentId()));
    }

    private void statusChanged(UnifiedOrder order) {
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getCustomerId(), order.getStatus()));
    }
}
