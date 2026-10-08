package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.event.AgentLocationUpdatedEvent;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.RiderRouteRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderStatusChangedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.realtime.RealtimePublisher;
import com.HyperLocalSystem.app.routing.RoutingClient;
import lombok.RequiredArgsConstructor;
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
 * Live tracking for customers: the snapshot behind GET /customer/orders/{id}/tracking, pushed on
 * /user/queue/tracking whenever the order's status changes or its rider moves.
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private final UnifiedOrderRepository orders;
    private final DeliveryAgentRepository agents;
    private final RiderRouteRepository routes;
    private final RouteStopRepository stops;
    private final RoutingClient routingClient;
    private final RealtimePublisher realtime;

    /** Rider position and the ETA to this customer along the rider's remaining (locked) route. */
    @Transactional(readOnly = true)
    public TrackingResponse snapshot(UnifiedOrder order) {
        DeliveryAgent agent = order.getDeliveryAgentId() == null ? null : agents.findById(order.getDeliveryAgentId()).orElse(null);
        GeoPoint position = agent == null ? null : GeoUtils.fromPoint(agent.getCurrentLocation());
        Integer etaMinutes = position != null && OrderStatus.ACTIVE.contains(order.getStatus())
                ? etaMinutes(agent.getId(), position, order.getId()) : null;
        return new TrackingResponse(order.getId(), order.getStatus(), position, etaMinutes, Instant.now());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        orders.findById(event.orderId()).ifPresent(this::push);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onAgentMoved(AgentLocationUpdatedEvent event) {
        orders.findAllById(stops.findActiveOrderIds(event.agentId())).forEach(this::push);
    }

    private void push(UnifiedOrder order) {
        realtime.sendTracking(order.getCustomerId(), snapshot(order));
    }

    /** Sum of legs from the rider through every unfinished stop up to this order's drop-off. */
    private Integer etaMinutes(UUID agentId, GeoPoint position, UUID orderId) {
        RiderRoute route = routes.findByAgentIdAndStatus(agentId, RiderRoute.Status.ACTIVE).orElse(null);
        if (route == null) {
            return null;
        }
        List<GeoPoint> path = new ArrayList<>(List.of(position));
        for (RouteStop stop : stops.findByRouteIdOrderBySequence(route.getId())) {
            if (stop.isDone()) {
                continue;
            }
            path.add(GeoUtils.fromPoint(stop.getLocation()));
            if (stop.getOrderId().equals(orderId) && stop.getType() == StopType.CUSTOMER) {
                return (int) Math.ceil(routingClient.route(path).duration().toSeconds() / 60.0);
            }
        }
        return null;
    }
}
