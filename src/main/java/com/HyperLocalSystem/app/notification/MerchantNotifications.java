package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.BroadcastClosedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.BroadcastOpenedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChange;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChangedEvent;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastTarget;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastOfferRepository;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastTargetRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.BroadcastTakenNotice;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.OrderCancelledNotice;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.OrderPickedUpNotice;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.RiderArrivingNotice;
import com.HyperLocalSystem.app.merchant.service.MerchantService;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.GroceriesUnfulfilledEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderCancelledEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderConfirmedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.realtime.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Merchant app messages on /user/queue/merchant-events. Everything comes from domain events after
 * they commit (asynchronously), never from controllers.
 */
@Component
@RequiredArgsConstructor
public class MerchantNotifications {

    private final UnifiedOrderRepository orders;
    private final BroadcastOfferRepository broadcastOffers;
    private final BroadcastTargetRepository broadcastTargets;
    private final RouteStopRepository stops;
    private final DeliveryAgentRepository agents;
    private final MerchantService merchantService;
    private final Notifier notifier;

    /** NEW_ORDER only once the payment is secured (CONFIRMED), never for a PENDING checkout. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        orders.findById(event.orderId()).filter(UnifiedOrder::hasFood).ifPresent(order ->
                notifier.merchant(order.getRestaurantId(), EventType.NEW_ORDER, merchantService.orderView(order, ItemType.FOOD)));
    }

    /** The restaurant and, if one claimed it, the Kirana. */
    /** The groceries were given up (nothing there, or the drop happened without them): the Kirana that claimed them closes the card. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onGroceriesUnfulfilled(GroceriesUnfulfilledEvent event) {
        orders.findById(event.orderId()).filter(order -> order.getKiranaId() != null).ifPresent(order ->
                notifier.merchant(order.getKiranaId(), EventType.ORDER_CANCELLED,
                        new OrderCancelledNotice(order.getId(), "The groceries for this order are no longer needed")));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderCancelled(OrderCancelledEvent event) {
        orders.findById(event.orderId()).ifPresent(order -> {
            var notice = new OrderCancelledNotice(order.getId(), order.getCancelReason());
            if (order.getRestaurantId() != null) {
                notifier.merchant(order.getRestaurantId(), EventType.ORDER_CANCELLED, notice);
            }
            if (order.getKiranaId() != null) {
                notifier.merchant(order.getKiranaId(), EventType.ORDER_CANCELLED, notice);
            }
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBroadcastOpened(BroadcastOpenedEvent event) {
        broadcastOffers.findById(event.broadcastId()).ifPresent(offer -> broadcastTargets.findByOfferId(offer.getId())
                .forEach(target -> notifier.merchant(target.getMerchantId(), EventType.BROADCAST_OFFER,
                        merchantService.broadcastView(offer, target.getMerchantId()))));
    }

    /** Every target except the winner loses the card. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onBroadcastClosed(BroadcastClosedEvent event) {
        broadcastOffers.findById(event.broadcastId()).ifPresent(offer -> {
            var notice = new BroadcastTakenNotice(offer.getId(), offer.getOrderId(), event.reason().name());
            broadcastTargets.findByOfferId(offer.getId()).stream()
                    .map(BroadcastTarget::getMerchantId)
                    .filter(merchantId -> !merchantId.equals(event.claimedBy()))
                    .forEach(merchantId -> notifier.merchant(merchantId, EventType.BROADCAST_TAKEN, notice));
        });
    }

    /** RIDER_ARRIVING when the rider taps Arrived at the store; ORDER_PICKED_UP when the pickup completes. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStopChanged(StopChangedEvent event) {
        RouteStop stop = event.stopId() == null ? null : stops.findById(event.stopId()).orElse(null);
        if (stop == null || stop.getType() == StopType.CUSTOMER || stop.getMerchantId() == null) {
            return;
        }
        if (event.change() == StopChange.ARRIVED) {
            agents.findById(stop.getAgentId()).ifPresent(rider -> notifier.merchant(stop.getMerchantId(), EventType.RIDER_ARRIVING,
                    new RiderArrivingNotice(stop.getOrderId(), stop.getId(), rider.getName(), rider.getPhone(),
                            rider.getVehicleNumber(), stop.getArrivedAt())));
        } else if (event.change() == StopChange.COMPLETED) {
            notifier.merchant(stop.getMerchantId(), EventType.ORDER_PICKED_UP,
                    new OrderPickedUpNotice(stop.getOrderId(), stop.getId(), stop.getCompletedAt()));
        }
    }
}
