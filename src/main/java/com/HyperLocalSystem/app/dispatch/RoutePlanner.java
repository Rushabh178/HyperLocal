package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.ActiveRouteResponse;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.StopItem;
import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.customer.service.CustomerAddressService;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.entity.TaskOffer;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.repository.OrderItemRepository;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.routing.RoutingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds and describes riders' linear routes. One order always runs Kirana → Restaurant → Customer;
 * the rider works stops strictly in sequence (Linear Route Locking).
 */
@Service
@RequiredArgsConstructor
public class RoutePlanner {

    private final MerchantRepository merchants;
    private final CustomerAddressRepository addresses;
    private final OrderItemRepository orderItems;
    private final UnifiedOrderRepository orders;
    private final DeliveryAgentRepository agents;
    private final RoutingClient routingClient;
    private final AppProperties properties;

    /** The order's stops that still make sense to visit, numbered from {@code firstSequence}. */
    public List<RouteStop> stopsFor(UnifiedOrder order, RiderRoute route, int firstSequence) {
        List<RouteStop> result = new ArrayList<>();
        if (order.getKiranaId() != null && order.getGroceryStatus() == PartStatus.READY) {
            result.add(pickupStop(route, order, StopType.KIRANA, order.getKiranaId()));
        }
        if (order.hasFood() && !order.getFoodStatus().isTerminal()) {
            result.add(pickupStop(route, order, StopType.RESTAURANT, order.getRestaurantId()));
        }
        result.add(customerStop(route, order));
        for (int i = 0; i < result.size(); i++) {
            result.get(i).setSequence(firstSequence + i);
        }
        return result;
    }

    public RouteStop pickupStop(RiderRoute route, UnifiedOrder order, StopType type, UUID merchantId) {
        Merchant merchant = merchants.findById(merchantId).orElseThrow(() -> ApiException.notFound("Store"));
        RouteStop stop = newStop(route, order, type);
        stop.setMerchantId(merchantId);
        stop.setName(merchant.getStoreName());
        stop.setAddress(merchant.getAddress());
        stop.setLocation(merchant.getLocation());
        return stop;
    }

    private RouteStop customerStop(RiderRoute route, UnifiedOrder order) {
        RouteStop stop = newStop(route, order, StopType.CUSTOMER);
        var address = order.getDeliveryAddressId() == null ? null : addresses.findById(order.getDeliveryAddressId()).orElse(null);
        stop.setName(address != null && address.getRecipientName() != null ? address.getRecipientName() : "Customer");
        stop.setAddress(address == null ? null : CustomerAddressService.toResponse(address).fullAddress());
        stop.setLocation(order.getDropLocation());
        return stop;
    }

    private static RouteStop newStop(RiderRoute route, UnifiedOrder order, StopType type) {
        RouteStop stop = new RouteStop();
        stop.setRouteId(route.getId());
        stop.setAgentId(route.getAgentId());
        stop.setOrderId(order.getId());
        stop.setType(type);
        return stop;
    }

    /** The stop the rider must work next: the lowest unfinished one. */
    public static Optional<RouteStop> currentStop(List<RouteStop> ordered) {
        return ordered.stream().filter(stop -> !stop.isDone()).findFirst();
    }

    /**
     * Recomputes ETAs from the rider's position along the unfinished stops. A restaurant stop never
     * gets an ETA before its order's prep deadline (the rider waits there).
     */
    public void refreshEtas(UUID agentId, List<RouteStop> ordered) {
        List<RouteStop> remaining = ordered.stream().filter(stop -> !stop.isDone()).toList();
        if (remaining.isEmpty()) {
            return;
        }
        Map<UUID, Instant> prepReadyAt = orders.findAllById(remaining.stream().map(RouteStop::getOrderId).distinct().toList())
                .stream().filter(o -> o.getPrepReadyAt() != null)
                .collect(Collectors.toMap(UnifiedOrder::getId, UnifiedOrder::getPrepReadyAt));
        GeoPoint from = agentLocation(agentId).orElse(GeoUtils.fromPoint(remaining.getFirst().getLocation()));
        Instant time = Instant.now();
        for (RouteStop stop : remaining) {
            GeoPoint to = GeoUtils.fromPoint(stop.getLocation());
            time = time.plus(routingClient.route(List.of(from, to)).duration());
            Instant ready = prepReadyAt.get(stop.getOrderId());
            if (stop.getType() == StopType.RESTAURANT && ready != null && ready.isAfter(time)) {
                time = ready;
            }
            stop.setEta(time);
            from = to;
        }
    }

    public ActiveRouteResponse toResponse(RiderRoute route, List<RouteStop> ordered) {
        Map<UUID, List<OrderItem>> items = itemsByOrder(ordered.stream().map(RouteStop::getOrderId).distinct().toList());
        List<RouteStop> remaining = ordered.stream().filter(stop -> !stop.isDone()).toList();
        int remainingMeters = 0;
        if (!remaining.isEmpty()) {
            List<GeoPoint> path = new ArrayList<>();
            agentLocation(route.getAgentId()).ifPresent(path::add);
            remaining.forEach(stop -> path.add(GeoUtils.fromPoint(stop.getLocation())));
            remainingMeters = routingClient.route(path).distanceMeters();
        }
        Instant completion = remaining.isEmpty() ? null : remaining.getLast().getEta();
        return new ActiveRouteResponse(route.getId(), true,
                ordered.stream().map(stop -> toDto(stop, items.getOrDefault(stop.getOrderId(), List.of()))).toList(),
                remainingMeters, completion);
    }

    /** The offer card: where the rider would go and roughly what they'd earn. */
    public AgentTaskDtos.TaskOffer toOfferDto(TaskOffer offer) {
        UnifiedOrder order = orders.findById(offer.getOrderId()).orElseThrow(() -> ApiException.notFound("Order"));
        RiderRoute preview = new RiderRoute(offer.getAgentId());
        List<RouteStop> stops = stopsFor(order, preview, 1);
        Map<UUID, List<OrderItem>> items = itemsByOrder(List.of(order.getId()));
        return new AgentTaskDtos.TaskOffer(offer.getId(), List.of(order.getId()),
                stops.stream().map(stop -> toDto(stop, items.getOrDefault(order.getId(), List.of()))).toList(),
                offer.getDistanceMeters(), offer.getEstimatedEarnings(), offer.getExpiresAt());
    }

    /** Base pay plus the multi-stop bonus for each pickup after the first. */
    public BigDecimal estimatedEarnings(UnifiedOrder order) {
        int pickups = (order.hasGroceries() ? 1 : 0) + (order.hasFood() ? 1 : 0);
        return properties.ops().riderBasePay()
                .add(properties.ops().riderMultiStopBonus().multiply(BigDecimal.valueOf(Math.max(0, pickups - 1))));
    }

    public Optional<GeoPoint> agentLocation(UUID agentId) {
        return agents.findById(agentId).map(DeliveryAgent::getCurrentLocation).map(GeoUtils::fromPoint);
    }

    private AgentTaskDtos.RouteStop toDto(RouteStop stop, List<OrderItem> orderLines) {
        ItemType wanted = switch (stop.getType()) {
            case KIRANA, DARK_STORE -> ItemType.GROCERY;
            case RESTAURANT -> ItemType.FOOD;
            case CUSTOMER -> null;
        };
        List<StopItem> stopItems = wanted == null ? List.of() : orderLines.stream()
                .filter(line -> line.getType() == wanted)
                .map(line -> new StopItem(line.getId(), line.getName(), line.getQuantity(), line.getZone(), line.isDeficient()))
                .toList();
        return new AgentTaskDtos.RouteStop(stop.getId(), stop.getSequence(), stop.getType(), stop.getName(), stop.getAddress(),
                GeoUtils.fromPoint(stop.getLocation()), List.of(stop.getOrderId()), stopItems, stop.getState(), stop.getEta());
    }

    private Map<UUID, List<OrderItem>> itemsByOrder(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItems.findByOrderIdInOrderByCreatedAt(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    /** What a rider without an active route sees. */
    public static ActiveRouteResponse emptyRoute() {
        return new ActiveRouteResponse(null, true, List.of(), 0, null);
    }

    /** Marks every unfinished stop of an order SKIPPED (cancellation, nothing left to pick up). */
    public static void skipRemaining(List<RouteStop> orderStops) {
        orderStops.stream().filter(stop -> !stop.isDone()).forEach(stop -> stop.setState(StopState.SKIPPED));
    }
}
