package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.cart.service.BillCalculator;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.GroceriesUnfulfilledEvent;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The one way a grocery part ends UNFULFILLED, from every path: the Kirana broadcast gave up, nothing
 * was available at the Kirana, or the customer drop completed without the groceries. The customer is
 * never charged for groceries that weren't picked up.
 * <ul>
 *   <li>Food carries on: the groceries' share is refunded to the wallet: their subtotal, their GST
 *       (none, groceries are tax-inclusive) and any delivery fee the food alone would not have paid
 *       (the meal waiver is recomputed). The convenience fee is per order and stays.</li>
 *   <li>Nothing remains: the order is cancelled, which releases the hold or refunds everything.</li>
 * </ul>
 * Idempotent per order part: the refund is a single wallet entry under {@link #refundReference}, so two
 * paths firing for the same order can never refund twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnfulfilledGroceries {

    private final OrderLifecycle lifecycle;
    private final PaymentService paymentService;
    private final BillCalculator billCalculator;
    private final ApplicationEventPublisher events;

    public static String refundReference(UUID orderId) {
        return "refund:groceries:" + orderId;
    }

    /**
     * Marks the grocery part UNFULFILLED (if it isn't already) and gives the money back.
     *
     * @return what was refunded to the wallet by this call
     */
    @Transactional
    public BigDecimal apply(UnifiedOrder order, String reason) {
        PartStatus status = order.getGroceryStatus();
        if (status == null || status == PartStatus.PICKED_UP || status == PartStatus.CANCELLED) {
            return BigDecimal.ZERO;
        }
        boolean changed = status != PartStatus.UNFULFILLED;
        if (changed) {
            order.moveGroceryTo(PartStatus.UNFULFILLED);
        }
        if (!order.hasFood()) {
            if (OrderStatus.ACTIVE.contains(order.getStatus())) {
                lifecycle.cancel(order, reason);
            }
            return BigDecimal.ZERO;
        }
        BigDecimal refund = paymentService.partialRefundToWallet(order.getId(), groceryShare(order), reason,
                refundReference(order.getId()));
        if (changed || refund.signum() > 0) {
            log.info("Groceries of order {} unfulfilled ({}), refunded {}", order.getId(), reason, refund);
            events.publishEvent(new GroceriesUnfulfilledEvent(order.getId(), order.getCustomerId(), refund, reason));
        }
        return refund;
    }

    /** What the groceries added to the bill: their subtotal, plus any delivery fee the food alone wouldn't have paid. */
    BigDecimal groceryShare(UnifiedOrder order) {
        BigDecimal foodOnlyFee = billCalculator.deliveryFee(order.getFoodSubtotal(), BigDecimal.ZERO);
        BigDecimal feeBack = order.getDeliveryFee().subtract(foodOnlyFee).max(BigDecimal.ZERO);
        return BillCalculator.money(order.getGrocerySubtotal().add(feeBack));
    }
}
