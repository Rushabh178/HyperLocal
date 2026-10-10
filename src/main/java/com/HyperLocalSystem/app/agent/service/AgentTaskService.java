package com.HyperLocalSystem.app.agent.service;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.ActiveRouteResponse;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliverRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.DeliveryHistoryItem;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.EarningsSummary;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.KiranaQrScanRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RejectOfferRequest;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.TaskOffer;
import com.HyperLocalSystem.app.agent.entity.AgentEarning;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.DutyStatus;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import com.HyperLocalSystem.app.agent.repository.AgentDutySessionRepository;
import com.HyperLocalSystem.app.agent.repository.AgentEarningRepository;
import com.HyperLocalSystem.app.agent.repository.AgentPayoutRepository;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.common.api.EarningsPeriod;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.api.PayoutItem;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.RiderNeededEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChange;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.StopChangedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchOrchestrator;
import com.HyperLocalSystem.app.dispatch.RoutePlanner;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.RiderRouteRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteInjectionRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.dispatch.repository.TaskOfferRepository;
import com.HyperLocalSystem.app.merchant.service.StoreQrService;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.repository.OrderItemRepository;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.order.service.OrderLifecycle;
import com.HyperLocalSystem.app.order.service.UnfulfilledGroceries;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import com.HyperLocalSystem.app.security.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everything the partner does while on a trip. Every action checks that the partner is approved
 * and owns the route / stop, and stops can only be worked in sequence (Linear Route Locking).
 */
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final DeliveryAgentRepository agents;
    private final TaskOfferRepository taskOffers;
    private final RiderRouteRepository routes;
    private final RouteStopRepository stops;
    private final RouteInjectionRepository injections;
    private final UnifiedOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final AgentEarningRepository earnings;
    private final AgentPayoutRepository payouts;
    private final AgentDutySessionRepository dutySessions;
    private final RoutePlanner routePlanner;
    private final DispatchOrchestrator dispatch;
    private final OrderLifecycle lifecycle;
    private final PaymentService paymentService;
    private final UnfulfilledGroceries unfulfilledGroceries;
    private final StoreQrService storeQrService;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------------------- offers

    @Transactional(readOnly = true)
    public List<TaskOffer> offers(UUID agentId) {
        loadApproved(agentId);
        return taskOffers.findByAgentIdAndStatusAndExpiresAtAfterOrderByCreatedAt(agentId,
                com.HyperLocalSystem.app.dispatch.entity.TaskOffer.Status.OFFERED, Instant.now())
                .stream().map(routePlanner::toOfferDto).toList();
    }

    /**
     * First accept wins (conditional UPDATE on the offer, then on the rider's AVAILABLE status). The
     * rider becomes ACTIVE, the order PICKING, and a locked route Kirana → Restaurant → Customer is built.
     */
    @Transactional
    public ActiveRouteResponse acceptOffer(UUID agentId, UUID offerId) {
        loadApproved(agentId);
        var offer = taskOffers.findById(offerId)
                .filter(o -> o.getAgentId().equals(agentId))
                .orElseThrow(() -> ApiException.notFound("Offer"));
        Instant now = Instant.now();
        if (taskOffers.accept(offerId, agentId, now) != 1) {
            throw ApiException.conflict("OFFER_NO_LONGER_AVAILABLE", "This offer has expired or was withdrawn");
        }
        if (agents.markActive(agentId) != 1) {
            throw ApiException.conflict("RIDER_BUSY", "Finish your current trip (and go online) before accepting a new one");
        }
        UnifiedOrder order = orders.findById(offer.getOrderId()).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getDeliveryAgentId() != null) {
            throw ApiException.conflict("OFFER_NO_LONGER_AVAILABLE", "Another rider already has this order");
        }
        order.setDeliveryAgentId(agentId);
        lifecycle.moveTo(order, OrderStatus.PICKING);

        RiderRoute route = routes.save(new RiderRoute(agentId));
        List<RouteStop> routeStops = stops.saveAll(routePlanner.stopsFor(order, route, 1));
        routePlanner.refreshEtas(agentId, routeStops);
        events.publishEvent(new StopChangedEvent(null, order.getId(), StopChange.ADDED));
        return routePlanner.toResponse(route, routeStops);
    }

    @Transactional
    public void rejectOffer(UUID agentId, UUID offerId, RejectOfferRequest request) {
        var offer = taskOffers.findById(offerId)
                .filter(o -> o.getAgentId().equals(agentId))
                .orElseThrow(() -> ApiException.notFound("Offer"));
        String reason = request == null ? null : request.reason();
        if (taskOffers.reject(offerId, agentId, reason, Instant.now()) != 1) {
            throw ApiException.conflict("OFFER_NO_LONGER_AVAILABLE", "This offer has expired or was withdrawn");
        }
        events.publishEvent(new RiderNeededEvent(offer.getOrderId()));
    }

    // ----------------------------------------------------------------- route

    @Transactional
    public ActiveRouteResponse currentRoute(UUID agentId) {
        RiderRoute route = activeRoute(agentId);
        List<RouteStop> routeStops = stops.findByRouteIdOrderBySequence(route.getId());
        routePlanner.refreshEtas(agentId, routeStops);
        return routePlanner.toResponse(route, routeStops);
    }

    @Transactional
    public ActiveRouteResponse arrivedAtStop(UUID agentId, UUID stopId) {
        RouteStop stop = currentStop(agentId, stopId);
        stop.setState(StopState.ARRIVED);
        stop.setArrivedAt(Instant.now());
        events.publishEvent(new StopChangedEvent(stop.getId(), stop.getOrderId(), StopChange.ARRIVED));
        return routeResponse(agentId);
    }

    /**
     * Pickup step 2 (Kirana only, optional): mark what the shop doesn't have. Only between Arrived and
     * the QR scan; the money moves at the scan.
     */
    @Transactional
    public ActiveRouteResponse markMissing(UUID agentId, UUID stopId, List<UUID> itemIds) {
        RouteStop stop = arrivedPickup(agentId, stopId);
        requireType(stop, StopType.KIRANA);
        for (UUID itemId : itemIds) {
            OrderItem item = orderItems.findById(itemId)
                    .filter(i -> i.getOrderId().equals(stop.getOrderId()) && i.getType() == ItemType.GROCERY)
                    .orElseThrow(() -> ApiException.notFound("Item"));
            item.setDeficient(true);
        }
        events.publishEvent(new StopChangedEvent(stop.getId(), stop.getOrderId(), StopChange.ITEMS_UPDATED));
        return routeResponse(agentId);
    }

    /**
     * Pickup step 3, the same at every store: the store's QR completes the stop.
     * Kirana: the shop is settled for the available items only and the missing ones are refunded to
     * the customer's wallet (nothing available → the stop is skipped, and a grocery-only order is
     * cancelled). Restaurant: the food is picked up and the restaurant is settled. Blueprint step 4.
     */
    @Transactional
    public ActiveRouteResponse scanQr(UUID agentId, UUID stopId, KiranaQrScanRequest request) {
        RouteStop stop = arrivedPickup(agentId, stopId);
        if (request == null || request.qrPayload() == null || request.qrPayload().isBlank()) {
            throw ApiException.badRequest("QR_REQUIRED", "Scan the store's QR to finish the pickup");
        }
        if (!storeQrService.verify(request.qrPayload()).equals(stop.getMerchantId())) {
            throw ApiException.badRequest("QR_WRONG_STORE", "This QR belongs to a different store");
        }
        UnifiedOrder order = loadOrder(stop.getOrderId());
        if (stop.getType() == StopType.RESTAURANT) {
            order.moveFoodTo(PartStatus.PICKED_UP);
            complete(stop);
            paymentService.captureForMerchant(order.getId(), stop.getMerchantId(), order.getFoodSubtotal());
            afterPickup(order);
            return routeResponse(agentId);
        }

        List<OrderItem> groceries = orderItems.findByOrderIdAndTypeOrderByCreatedAt(order.getId(), ItemType.GROCERY);
        BigDecimal available = sum(groceries.stream().filter(item -> !item.isDeficient()).toList());
        BigDecimal missing = sum(groceries.stream().filter(OrderItem::isDeficient).toList());
        if (available.signum() == 0) {
            nothingAvailable(agentId, stop, order);
            return routeResponse(agentId);
        }
        order.moveGroceryTo(PartStatus.PICKED_UP);
        complete(stop);
        paymentService.captureForMerchant(order.getId(), stop.getMerchantId(), available);
        if (missing.signum() > 0) {
            paymentService.partialRefundToWallet(order.getId(), missing, "Items missing at " + stop.getName(),
                    "refund:missing:" + stop.getId());
        }
        afterPickup(order);
        return routeResponse(agentId);
    }

    /** Every item was missing: drop the stop; cancel a grocery-only order, refund the groceries of a combined one. */
    private void nothingAvailable(UUID agentId, RouteStop stop, UnifiedOrder order) {
        stop.setState(StopState.SKIPPED);
        events.publishEvent(new StopChangedEvent(stop.getId(), stop.getOrderId(), StopChange.SKIPPED));
        if (!order.hasFood()) {
            order.moveGroceryTo(PartStatus.UNFULFILLED);
            RoutePlanner.skipRemaining(stops.findByOrderIdOrderBySequence(order.getId()));
            lifecycle.cancel(order, "None of the items were available at the store");
            stops.flush();
            dispatch.completeRouteIfDone(agentId);
        } else {
            unfulfilledGroceries.apply(order, "None of the groceries were available at " + stop.getName());
            afterPickup(order);
        }
    }

    private static BigDecimal sum(List<OrderItem> items) {
        return items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Checks the customer's 4-digit OTP, completes the order, books the rider's earnings and frees
     * the rider when the route is empty.
     */
    @Transactional
    public ActiveRouteResponse deliver(UUID agentId, UUID orderId, DeliverRequest request) {
        UnifiedOrder order = orders.findById(orderId)
                .filter(o -> agentId.equals(o.getDeliveryAgentId()))
                .orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
            throw ApiException.conflict("ORDER_NOT_OUT_FOR_DELIVERY", "Pick up everything for this order first");
        }
        RouteStop drop = stops.findByOrderIdOrderBySequence(orderId).stream()
                .filter(s -> s.getType() == StopType.CUSTOMER).findFirst()
                .orElseThrow(() -> ApiException.notFound("Stop"));
        currentStop(agentId, drop.getId());
        if (!Hashing.constantTimeEquals(order.getDeliveryOtp(), request.deliveryOtp())) {
            throw ApiException.badRequest("DELIVERY_OTP_INVALID", "The code doesn't match. Ask the customer to check the app.");
        }
        // Groceries that never reached the bag aren't paid for (no-op if they were already given up and refunded).
        if (order.hasGroceries() && order.getGroceryStatus() != PartStatus.PICKED_UP) {
            unfulfilledGroceries.apply(order, "Your groceries couldn't be picked up");
        }
        complete(drop);
        lifecycle.moveTo(order, OrderStatus.COMPLETED);
        paymentService.ensureCaptured(orderId);
        bookEarnings(agentId, order);
        stops.flush();
        dispatch.completeRouteIfDone(agentId);
        return routeResponse(agentId);
    }

    @Transactional
    public void acknowledgeInjection(UUID agentId, UUID injectionId) {
        var injection = injections.findByIdAndAgentId(injectionId, agentId).orElseThrow(() -> ApiException.notFound("Injection"));
        if (injection.getAcknowledgedAt() == null) {
            injection.setAcknowledgedAt(Instant.now());
        }
    }

    // ----------------------------------------------------------------- history & earnings

    @Transactional(readOnly = true)
    public PageResponse<DeliveryHistoryItem> history(UUID agentId, int page, int size) {
        Page<UnifiedOrder> delivered = orders.findByDeliveryAgentIdAndStatusOrderByCompletedAtDesc(
                agentId, OrderStatus.COMPLETED, PageResponse.request(page, size));
        List<UUID> ids = delivered.getContent().stream().map(UnifiedOrder::getId).toList();
        Map<UUID, BigDecimal> paid = ids.isEmpty() ? Map.of() : earnings.totalsByOrder(agentId, ids).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
        Map<UUID, Long> stopCounts = ids.isEmpty() ? Map.of() : stops.findByOrderIdIn(ids).stream()
                .filter(s -> s.getState() == StopState.COMPLETED)
                .collect(Collectors.groupingBy(RouteStop::getOrderId, Collectors.counting()));
        return PageResponse.of(delivered, o -> new DeliveryHistoryItem(o.getId(), o.isBatched(),
                stopCounts.getOrDefault(o.getId(), 0L).intValue(), paid.getOrDefault(o.getId(), BigDecimal.ZERO),
                o.getCompletedAt()));
    }

    /** Totals for today / this week (from Monday) / this month in Indian time, today's online minutes, payouts. */
    @Transactional(readOnly = true)
    public EarningsSummary earnings(UUID agentId, EarningsPeriod period, int page, int size) {
        LocalDate today = LocalDate.now(EarningsPeriod.INDIA);
        LocalDate from = period.firstDay(today);
        Map<AgentEarning.Type, Object[]> byType = new EnumMap<>(AgentEarning.Type.class);
        earnings.totalsByType(agentId, EarningsPeriod.startOf(from), EarningsPeriod.startOf(today.plusDays(1)))
                .forEach(row -> byType.put((AgentEarning.Type) row[0], row));
        BigDecimal base = sum(byType, AgentEarning.Type.BASE);
        BigDecimal multiStop = sum(byType, AgentEarning.Type.MULTI_STOP_BONUS);
        BigDecimal incentives = sum(byType, AgentEarning.Type.BATCH_INCENTIVE);
        return new EarningsSummary(period, from, today, (int) count(byType, AgentEarning.Type.BASE),
                (int) count(byType, AgentEarning.Type.BATCH_INCENTIVE), base, multiStop, incentives,
                base.add(multiStop).add(incentives), onlineMinutesToday(agentId, today), payouts(agentId, page, size));
    }

    /** Minutes online since midnight (Indian time), counting a session that is still open up to now. */
    private long onlineMinutesToday(UUID agentId, LocalDate today) {
        Instant dayStart = EarningsPeriod.startOf(today);
        Instant now = Instant.now();
        long seconds = dutySessions.findOverlapping(agentId, dayStart).stream()
                .mapToLong(s -> {
                    Instant start = s.getStartedAt().isAfter(dayStart) ? s.getStartedAt() : dayStart;
                    Instant end = s.getEndedAt() == null || s.getEndedAt().isAfter(now) ? now : s.getEndedAt();
                    return Math.max(0, Duration.between(start, end).toSeconds());
                })
                .sum();
        return seconds / 60;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayoutItem> payouts(UUID agentId, int page, int size) {
        return PageResponse.of(payouts.findByAgentIdOrderByCreatedAtDesc(agentId, PageResponse.request(page, size)),
                p -> new PayoutItem(p.getId(), p.getAmount(), p.getStatus().name(), p.getUtr(), p.getPaidAt()));
    }

    // ----------------------------------------------------------------- helpers

    private DeliveryAgent loadApproved(UUID agentId) {
        DeliveryAgent agent = agents.findById(agentId).orElseThrow(() -> ApiException.notFound("Delivery partner"));
        if (agent.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw ApiException.forbidden("AGENT_NOT_APPROVED", "Your profile must be approved before you can take orders");
        }
        if (agent.getDutyStatus() == DutyStatus.OFFLINE) {
            throw ApiException.conflict("AGENT_OFFLINE", "Go online to take orders");
        }
        return agent;
    }

    private RiderRoute activeRoute(UUID agentId) {
        return routes.findByAgentIdAndStatus(agentId, RiderRoute.Status.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Active route"));
    }

    /** A pickup stop the rider is standing at: not finished yet, next in sequence, Arrived tapped. */
    private RouteStop arrivedPickup(UUID agentId, UUID stopId) {
        RouteStop stop = stops.findByIdAndAgentId(stopId, agentId).orElseThrow(() -> ApiException.notFound("Stop"));
        if (stop.isDone()) {
            throw ApiException.conflict("STOP_ALREADY_COMPLETED", "This pickup is already done");
        }
        if (!stop.isPickup()) {
            throw ApiException.badRequest("WRONG_STOP_TYPE", "This action is for a store pickup");
        }
        RouteStop current = currentStop(agentId, stopId);
        if (current.getState() != StopState.ARRIVED) {
            throw ApiException.conflict("NOT_ARRIVED", "Tap Arrived when you reach the store");
        }
        return current;
    }

    /** The stop must be the rider's and the next one in sequence. */
    private RouteStop currentStop(UUID agentId, UUID stopId) {
        RouteStop stop = stops.findByIdAndAgentId(stopId, agentId).orElseThrow(() -> ApiException.notFound("Stop"));
        RouteStop current = RoutePlanner.currentStop(stops.findByRouteIdOrderBySequence(stop.getRouteId()))
                .orElseThrow(() -> ApiException.conflict("ROUTE_FINISHED", "This route has no stops left"));
        if (!current.getId().equals(stopId)) {
            throw ApiException.conflict("STOP_LOCKED", "Finish stop " + current.getSequence() + " (" + current.getName() + ") first");
        }
        return current;
    }

    private static void requireType(RouteStop stop, StopType type) {
        if (stop.getType() != type) {
            throw ApiException.badRequest("WRONG_STOP_TYPE", "This action is for a " + type + " stop");
        }
    }

    private void complete(RouteStop stop) {
        stop.setState(StopState.COMPLETED);
        stop.setCompletedAt(Instant.now());
        events.publishEvent(new StopChangedEvent(stop.getId(), stop.getOrderId(), StopChange.COMPLETED));
    }

    /** All pickups of the order done → the rider heads to the customer. */
    private void afterPickup(UnifiedOrder order) {
        List<RouteStop> orderStops = stops.findByOrderIdOrderBySequence(order.getId());
        boolean pickupsDone = orderStops.stream().filter(RouteStop::isPickup).allMatch(RouteStop::isDone);
        boolean canLeave = order.getStatus() == OrderStatus.PICKING || order.getStatus() == OrderStatus.BATCHED;
        if (pickupsDone && canLeave) {
            lifecycle.moveTo(order, OrderStatus.OUT_FOR_DELIVERY);
            orderStops.stream().filter(s -> s.getType() == StopType.CUSTOMER && !s.isDone())
                    .forEach(s -> s.setState(StopState.EN_ROUTE));
        }
    }

    /** Base pay per order, plus the multi-stop bonus for every pickup after the first. */
    private void bookEarnings(UUID agentId, UnifiedOrder order) {
        AppProperties.Ops ops = properties.ops();
        long pickups = stops.findByOrderIdOrderBySequence(order.getId()).stream()
                .filter(s -> s.isPickup() && s.getState() == StopState.COMPLETED).count();
        if (!earnings.existsByAgentIdAndOrderIdAndType(agentId, order.getId(), AgentEarning.Type.BASE)) {
            earnings.save(new AgentEarning(agentId, order.getId(), AgentEarning.Type.BASE, ops.riderBasePay()));
        }
        if (pickups > 1 && !earnings.existsByAgentIdAndOrderIdAndType(agentId, order.getId(), AgentEarning.Type.MULTI_STOP_BONUS)) {
            earnings.save(new AgentEarning(agentId, order.getId(), AgentEarning.Type.MULTI_STOP_BONUS,
                    ops.riderMultiStopBonus().multiply(BigDecimal.valueOf(pickups - 1))));
        }
    }

    private UnifiedOrder loadOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    private ActiveRouteResponse routeResponse(UUID agentId) {
        stops.flush();
        RiderRoute route = routes.findByAgentIdAndStatus(agentId, RiderRoute.Status.ACTIVE).orElse(null);
        if (route == null) {
            return RoutePlanner.emptyRoute();
        }
        List<RouteStop> routeStops = stops.findByRouteIdOrderBySequence(route.getId());
        routePlanner.refreshEtas(agentId, routeStops);
        return routePlanner.toResponse(route, routeStops);
    }

    private static BigDecimal sum(Map<AgentEarning.Type, Object[]> byType, AgentEarning.Type type) {
        Object[] row = byType.get(type);
        return row == null ? BigDecimal.ZERO : (BigDecimal) row[2];
    }

    private static long count(Map<AgentEarning.Type, Object[]> byType, AgentEarning.Type type) {
        Object[] row = byType.get(type);
        return row == null ? 0 : (Long) row[1];
    }
}
