package com.HyperLocalSystem.app.batching;

import com.HyperLocalSystem.app.agent.entity.AgentEarning;
import com.HyperLocalSystem.app.agent.repository.AgentEarningRepository;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.batching.CoLinearBatchingAlgorithm.Plan;
import com.HyperLocalSystem.app.batching.CoLinearBatchingAlgorithm.Point;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.OfferWithdrawnEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.WithdrawReason;
import com.HyperLocalSystem.app.dispatch.RoutePlanner;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.entity.RouteInjection;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.RiderRouteRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteInjectionRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.dispatch.repository.TaskOfferRepository;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.service.OrderLifecycle;
import com.HyperLocalSystem.app.routing.RoutingClient;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Pathfinder: Dynamic Co-Linear Batching Engine (blueprint §3). For a claimed grocery-only
 * order it looks for a rider already on a trip whose locked route passes the Kirana and the
 * customer, and slips the two stops in if that costs at most {@code ops.batchingMaxAddedDelay}
 * (4 min). The maths lives in {@link CoLinearBatchingAlgorithm}; this class only loads candidates
 * and applies the winning plan.
 */
@Slf4j
@Service
public class CoLinearBatchingEngine {

    public record Injection(UUID agentId, UUID primaryOrderId, UUID injectedOrderId, Duration addedDelay, BigDecimal bonus) {
    }

    /** After commit, push the "+1 stop" flash to the rider. */
    public record RouteInjectedEvent(UUID injectionId, UUID agentId) {
    }

    private final DeliveryAgentRepository agents;
    private final MerchantRepository merchants;
    private final RiderRouteRepository routes;
    private final RouteStopRepository stops;
    private final RouteInjectionRepository injections;
    private final TaskOfferRepository taskOffers;
    private final AgentEarningRepository earnings;
    private final RoutePlanner routePlanner;
    private final OrderLifecycle lifecycle;
    private final SpatialIndexService spatialIndex;
    private final RoutingClient routingClient;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;
    private final CoLinearBatchingAlgorithm algorithm;

    public CoLinearBatchingEngine(DeliveryAgentRepository agents, MerchantRepository merchants, RiderRouteRepository routes,
                                  RouteStopRepository stops, RouteInjectionRepository injections, TaskOfferRepository taskOffers,
                                  AgentEarningRepository earnings, RoutePlanner routePlanner, OrderLifecycle lifecycle,
                                  SpatialIndexService spatialIndex, RoutingClient routingClient, AppProperties properties,
                                  ApplicationEventPublisher events) {
        this.agents = agents;
        this.merchants = merchants;
        this.routes = routes;
        this.stops = stops;
        this.injections = injections;
        this.taskOffers = taskOffers;
        this.earnings = earnings;
        this.routePlanner = routePlanner;
        this.lifecycle = lifecycle;
        this.spatialIndex = spatialIndex;
        this.routingClient = routingClient;
        this.properties = properties;
        this.events = events;
        this.algorithm = new CoLinearBatchingAlgorithm(properties.ops().batchingCorridorMeters(),
                properties.ops().batchingMaxAddedDelay().toSeconds());
    }

    /**
     * Tries to add a claimed grocery-only order to an active rider's route. Empty means "no rider
     * qualifies": the caller falls back to normal dispatch. A concurrent change to the chosen route
     * fails the transaction (route @Version), and the scheduler then dispatches the order normally.
     */
    @Transactional
    public Optional<Injection> tryInject(UnifiedOrder order) {
        boolean eligible = !order.hasFood() && order.getGroceryStatus() == PartStatus.READY && order.getKiranaId() != null
                && order.getStatus() == OrderStatus.CONFIRMED && order.getDeliveryAgentId() == null;
        if (!eligible) {
            return Optional.empty();
        }
        Merchant kirana = merchants.findById(order.getKiranaId()).orElseThrow();
        Point pickup = point(GeoUtils.fromPoint(kirana.getLocation()));
        Point drop = point(GeoUtils.fromPoint(order.getDropLocation()));

        Candidate best = null;
        int radius = properties.ops().riderSearchRadiusMeters();
        for (UUID agentId : agents.findActiveIn(spatialIndex.cellsWithin(pickup.lat(), pickup.lng(), radius))) {
            Candidate candidate = evaluate(agentId, pickup, drop);
            if (candidate != null && (best == null || candidate.plan().addedSeconds() < best.plan().addedSeconds())) {
                best = candidate;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(apply(best, order));
    }

    private record Candidate(RiderRoute route, List<RouteStop> remaining, Plan plan) {
    }

    /** One rider: their unfinished stops in locked order, one travel-time matrix, the best insertion. */
    private Candidate evaluate(UUID agentId, Point pickup, Point drop) {
        RiderRoute route = routes.findByAgentIdAndStatus(agentId, RiderRoute.Status.ACTIVE).orElse(null);
        Optional<GeoPoint> position = routePlanner.agentLocation(agentId);
        if (route == null || position.isEmpty()) {
            return null;
        }
        List<RouteStop> remaining = stops.findByRouteIdOrderBySequence(route.getId()).stream()
                .filter(stop -> !stop.isDone()).toList();
        if (remaining.isEmpty()) {
            return null;
        }
        Point rider = point(position.get());
        List<Point> remainingPoints = remaining.stream().map(stop -> point(GeoUtils.fromPoint(stop.getLocation()))).toList();
        return algorithm.plan(rider, remainingPoints, pickup, drop, matrix(rider, remainingPoints, pickup, drop))
                .map(plan -> new Candidate(route, remaining, plan))
                .orElse(null);
    }

    /** Applies the plan: new stops in place, order BATCHED, ₹15 incentive, "+1 stop" push after commit. */
    private Injection apply(Candidate candidate, UnifiedOrder order) {
        RiderRoute route = candidate.route();
        UUID agentId = route.getAgentId();
        List<RouteStop> newStops = routePlanner.stopsFor(order, route, 0);
        RouteStop pickupStop = newStops.getFirst();
        RouteStop dropStop = newStops.getLast();

        List<RouteStop> remaining = candidate.remaining();
        List<RouteStop> sequence = new ArrayList<>(remaining.subList(0, candidate.plan().pickupAfter()));
        sequence.add(pickupStop);
        sequence.addAll(remaining.subList(candidate.plan().pickupAfter(), candidate.plan().dropAfter()));
        sequence.add(dropStop);
        sequence.addAll(remaining.subList(candidate.plan().dropAfter(), remaining.size()));
        int first = remaining.getFirst().getSequence();
        for (int i = 0; i < sequence.size(); i++) {
            sequence.get(i).setSequence(first + i);
        }
        stops.saveAll(List.of(pickupStop, dropStop));
        routePlanner.refreshEtas(agentId, sequence);
        route.setUpdatedAt(Instant.now());

        UUID primaryOrderId = remaining.getFirst().getOrderId();
        taskOffers.withdrawOpen(order.getId()).forEach(offer -> events.publishEvent(
                new OfferWithdrawnEvent(offer.getId(), offer.getAgentId(), offer.getOrderId(), WithdrawReason.TAKEN)));
        order.setDeliveryAgentId(agentId);
        order.setBatched(true);
        order.setParentBatchId(primaryOrderId);
        lifecycle.moveTo(order, OrderStatus.BATCHED);

        BigDecimal bonus = properties.ops().batchInjectionBonus();
        RouteInjection injection = new RouteInjection();
        injection.setRouteId(route.getId());
        injection.setAgentId(agentId);
        injection.setInjectedOrderId(order.getId());
        injection.setPrimaryOrderId(primaryOrderId);
        injection.setBonus(bonus);
        injection.setAddedDelaySeconds(candidate.plan().addedSeconds());
        injections.save(injection);
        earnings.save(new AgentEarning(agentId, order.getId(), AgentEarning.Type.BATCH_INCENTIVE, bonus));
        events.publishEvent(new RouteInjectedEvent(injection.getId(), agentId));
        log.info("Batched order {} into rider {}'s route (+{} s)", order.getId(), agentId, candidate.plan().addedSeconds());
        return new Injection(agentId, primaryOrderId, order.getId(), Duration.ofSeconds(candidate.plan().addedSeconds()), bonus);
    }

    /** Every leg the algorithm may need, from a single RoutingClient matrix call. */
    private CoLinearBatchingAlgorithm.TravelTime matrix(Point rider, List<Point> remaining, Point pickup, Point drop) {
        List<Point> points = new ArrayList<>();
        points.add(rider);
        points.addAll(remaining);
        points.add(pickup);
        points.add(drop);
        List<GeoPoint> geo = points.stream().map(p -> new GeoPoint(p.lat(), p.lng())).toList();
        long[][] seconds = routingClient.durationMatrix(geo, geo);
        Map<Point, Integer> index = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            index.putIfAbsent(points.get(i), i);
        }
        return (from, to) -> seconds[index.get(from)][index.get(to)];
    }

    private static Point point(GeoPoint geo) {
        return new Point(geo.lat(), geo.lng());
    }
}
