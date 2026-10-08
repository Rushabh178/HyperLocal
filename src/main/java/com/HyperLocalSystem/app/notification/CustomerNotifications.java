package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.agent.event.AgentLocationUpdatedEvent;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.order.dto.OrderDtos.RiderLocation;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderStatusChangedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.order.service.TrackingService;
import com.HyperLocalSystem.app.realtime.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/** Customer app messages on /user/queue/tracking. All after commit, asynchronously. */
@Component
@RequiredArgsConstructor
public class CustomerNotifications {

    private final UnifiedOrderRepository orders;
    private final DeliveryAgentRepository agents;
    private final RouteStopRepository stops;
    private final TrackingService trackingService;
    private final Notifier notifier;

    /** ORDER_STATUS carries the full tracking snapshot, so one message repaints the screen. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        orders.findById(event.orderId()).ifPresent(order ->
                notifier.customer(order.getCustomerId(), EventType.ORDER_STATUS, trackingService.snapshot(order)));
    }

    /** RIDER_LOCATION for every order the rider is still carrying. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onAgentMoved(AgentLocationUpdatedEvent event) {
        GeoPoint position = agents.findById(event.agentId())
                .map(agent -> GeoUtils.fromPoint(agent.getCurrentLocation())).orElse(null);
        if (position == null) {
            return;
        }
        orders.findAllById(stops.findActiveOrderIds(event.agentId())).forEach(order -> notifier.customer(
                order.getCustomerId(), EventType.RIDER_LOCATION, new RiderLocation(order.getId(), position,
                        trackingService.etaMinutes(event.agentId(), position, order.getId()), Instant.now())));
    }
}
