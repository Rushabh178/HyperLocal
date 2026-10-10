package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.batching.CoLinearBatchingEngine;
import com.HyperLocalSystem.app.agent.entity.DutyStatus;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.OfferWithdrawnEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.RiderNeededEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.RouteChangedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChange;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChangedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.WithdrawReason;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.TaskOfferCreatedEvent;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.entity.TaskOffer;
import com.HyperLocalSystem.app.dispatch.repository.RiderRouteRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.dispatch.repository.TaskOfferRepository;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.GroceriesUnfulfilledEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.KiranaClaimedEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.KitchenDelayedEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderCancelledEvent;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderConfirmedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.routing.RoutingClient;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Blueprint §3 "Parallel Dispatch Sequence". No HTTP endpoints: it reacts to order events after
 * they commit (asynchronously, each in its own transaction) and {@link DispatchScheduler} retries
 * whatever an event couldn't finish (no rider free yet, offer expired, round timed out).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchOrchestrator {

    private final UnifiedOrderRepository orders;
    private final TaskOfferRepository taskOffers;
    private final DeliveryAgentRepository agents;
    private final MerchantRepository merchants;
    private final RiderRouteRepository routes;
    private final RouteStopRepository stops;
    private final KiranaBroadcastService broadcastService;
    private final CoLinearBatchingEngine batchingEngine;
    private final RoutePlanner routePlanner;
    private final SpatialIndexService spatialIndex;
    private final RoutingClient routingClient;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------------------- event listeners

    /**
     * Payment secured. Groceries go to the Kirana broadcast (held back a little for combined orders so
     * they aren't picked up long before the food); a food-only order gets a rider straight away. An
     * order with groceries (mixed or not) waits for a Kirana to claim them, or for them to be given
     * up, before looking for a rider: see {@link UnifiedOrder#readyForRider()}.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        UnifiedOrder order = orders.findById(event.orderId()).orElseThrow();
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return;
        }
        if (order.hasGroceries()) {
            Instant startsAt = order.hasFood() ? Instant.now().plus(properties.ops().kiranaBroadcastCombinedDelay()) : Instant.now();
            broadcastService.schedule(order, 1, startsAt);
        }
        if (order.readyForRider()) {
            offerNextRider(order);
        }
    }

    /**
     * A Kirana has the groceries: the order can now get a rider (mixed orders were held for this).
     * An order that already has one (dispatched before the hold existed) gets the Kirana stop added.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onKiranaClaimed(KiranaClaimedEvent event) {
        UnifiedOrder order = orders.findById(event.orderId()).orElseThrow();
        if (order.getDeliveryAgentId() != null) {
            insertKiranaStop(order);
        } else {
            dispatch(order);
        }
    }

    /** The groceries were given up: close any round still running, and send the food on alone. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGroceriesUnfulfilled(GroceriesUnfulfilledEvent event) {
        broadcastService.cancelFor(event.orderId());
        // Same guard as the poller: skip if an offer is already out (e.g. made by onOrderConfirmed).
        orders.lockIfAwaitingRider(event.orderId()).ifPresent(this::offerNextRider);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onKitchenDelayed(KitchenDelayedEvent event) {
        broadcastService.postpone(event.orderId(), event.delay());
    }

    /** Stop everything still running for the order and free the rider if this was their last stop. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCancelled(OrderCancelledEvent event) {
        taskOffers.withdrawOpen(event.orderId()).forEach(offer -> events.publishEvent(
                new OfferWithdrawnEvent(offer.getId(), offer.getAgentId(), offer.getOrderId(), WithdrawReason.CANCELLED)));
        broadcastService.cancelFor(event.orderId());
        RoutePlanner.skipRemaining(stops.findByOrderIdOrderBySequence(event.orderId()));
        if (event.deliveryAgentId() != null) {
            stops.flush();
            completeRouteIfDone(event.deliveryAgentId());
            events.publishEvent(new RouteChangedEvent(event.deliveryAgentId()));
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRiderNeeded(RiderNeededEvent event) {
        orders.findById(event.orderId()).ifPresent(this::offerNextRider);
    }

    // ----------------------------------------------------------------- dispatch

    /**
     * Blueprint step 3 for a solo grocery order: first try to slip it into an active rider's route
     * (co-linear batching); only if nobody qualifies, offer it to the nearest free rider.
     */
    @Transactional
    public void dispatch(UnifiedOrder order) {
        if (!order.hasFood() && batchingEngine.tryInject(order).isPresent()) {
            return;
        }
        offerNextRider(order);
    }

    /**
     * Offers the order to the nearest free rider around its first pickup who hasn't seen it yet.
     * Returns false if nobody is free; the scheduler tries again on its next tick.
     * TODO(edge-case): once every nearby rider has declined, widen the radius / re-offer / alert ops.
     */
    @Transactional
    public boolean offerNextRider(UnifiedOrder order) {
        boolean needsRider = order.getStatus() == OrderStatus.CONFIRMED && order.getDeliveryAgentId() == null
                && order.readyForRider();
        if (!needsRider) {
            return false;
        }
        GeoPoint pickup = firstPickup(order);
        int radius = properties.ops().riderSearchRadiusMeters();
        List<UUID> riders = agents.findNearestAvailable(spatialIndex.cellsWithin(pickup.lat(), pickup.lng(), radius),
                pickup.lat(), pickup.lng(), radius, order.getId(), 1);
        if (riders.isEmpty()) {
            log.info("No free rider near order {} yet", order.getId());
            return false;
        }
        UUID agentId = riders.getFirst();
        TaskOffer offer = new TaskOffer();
        offer.setAgentId(agentId);
        offer.setOrderId(order.getId());
        offer.setExpiresAt(Instant.now().plus(properties.ops().riderOfferTimeout()));
        offer.setDistanceMeters(tripMeters(agentId, order, pickup));
        offer.setEstimatedEarnings(routePlanner.estimatedEarnings(order));
        // The partial unique index (one OFFERED per order) rejects a duplicate from another instance.
        taskOffers.saveAndFlush(offer);
        events.publishEvent(new TaskOfferCreatedEvent(offer.getId(), agentId));
        log.info("Offered order {} to rider {}", order.getId(), agentId);
        return true;
    }

    /** Kirana pickup goes right before the order's restaurant stop (or before its drop-off). */
    @Transactional
    public void insertKiranaStop(UnifiedOrder order) {
        RiderRoute route = routes.findByAgentIdAndStatus(order.getDeliveryAgentId(), RiderRoute.Status.ACTIVE).orElse(null);
        if (route == null) {
            return;
        }
        List<RouteStop> routeStops = new ArrayList<>(stops.findByRouteIdOrderBySequence(route.getId()));
        boolean alreadyThere = routeStops.stream()
                .anyMatch(s -> s.getOrderId().equals(order.getId()) && s.getType() == StopType.KIRANA);
        RouteStop before = routeStops.stream()
                .filter(s -> s.getOrderId().equals(order.getId()) && !s.isDone()
                        && (s.getType() == StopType.RESTAURANT || s.getType() == StopType.CUSTOMER))
                .findFirst().orElse(null);
        if (alreadyThere || before == null) {
            return;
        }
        int sequence = before.getSequence();
        routeStops.stream().filter(s -> s.getSequence() >= sequence).forEach(s -> s.setSequence(s.getSequence() + 1));
        RouteStop kirana = routePlanner.pickupStop(route, order, StopType.KIRANA, order.getKiranaId());
        kirana.setSequence(sequence);
        stops.save(kirana);
        events.publishEvent(new StopChangedEvent(kirana.getId(), order.getId(), StopChange.ADDED));
        routeStops.add(kirana);
        routeStops.sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));
        routePlanner.refreshEtas(route.getAgentId(), routeStops);
        route.setUpdatedAt(Instant.now());
        events.publishEvent(new RouteChangedEvent(route.getAgentId()));
        // TODO(edge-case): if the food was already picked up, hot items now wait while the rider goes to the Kirana.
    }

    /** When every stop is done the route closes and the rider is free for the next order. */
    @Transactional
    public void completeRouteIfDone(UUID agentId) {
        routes.findByAgentIdAndStatus(agentId, RiderRoute.Status.ACTIVE).ifPresent(route -> {
            if (stops.findByRouteIdOrderBySequence(route.getId()).stream().allMatch(RouteStop::isDone)) {
                route.setStatus(RiderRoute.Status.COMPLETED);
                route.setCompletedAt(Instant.now());
                agents.findById(agentId).filter(a -> a.getDutyStatus() == DutyStatus.ACTIVE)
                        .ifPresent(a -> a.setDutyStatus(DutyStatus.AVAILABLE));
            }
        });
    }

    // ----------------------------------------------------------------- helpers

    private GeoPoint firstPickup(UnifiedOrder order) {
        UUID merchantId = order.isGroceryClaimed()
                ? order.getKiranaId() : order.getRestaurantId();
        return merchants.findById(merchantId).map(m -> GeoUtils.fromPoint(m.getLocation())).orElseThrow();
    }

    /** Rider → first pickup → drop, for the offer card. */
    private int tripMeters(UUID agentId, UnifiedOrder order, GeoPoint pickup) {
        List<GeoPoint> path = new ArrayList<>();
        agents.findById(agentId).map(DeliveryAgent::getCurrentLocation).map(GeoUtils::fromPoint).ifPresent(path::add);
        path.add(pickup);
        path.add(GeoUtils.fromPoint(order.getDropLocation()));
        return routingClient.route(path).distanceMeters();
    }
}
