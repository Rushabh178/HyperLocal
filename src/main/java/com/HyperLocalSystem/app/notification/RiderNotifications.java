package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RouteInjectionNotice;
import com.HyperLocalSystem.app.batching.CoLinearBatchingEngine.RouteInjectedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.OfferWithdrawnEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.RouteChangedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.TaskOfferCreatedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.WithdrawReason;
import com.HyperLocalSystem.app.dispatch.RoutePlanner;
import com.HyperLocalSystem.app.dispatch.entity.RiderRoute;
import com.HyperLocalSystem.app.dispatch.repository.RiderRouteRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteInjectionRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.dispatch.repository.TaskOfferRepository;
import com.HyperLocalSystem.app.realtime.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/** Rider app messages on /user/queue/route-updates. All after commit, asynchronously. */
@Component
@RequiredArgsConstructor
public class RiderNotifications {

    public record OfferWithdrawn(UUID offerId, UUID orderId, WithdrawReason reason) {
    }

    private final TaskOfferRepository taskOffers;
    private final RouteInjectionRepository injections;
    private final RiderRouteRepository routes;
    private final RouteStopRepository stops;
    private final RoutePlanner routePlanner;
    private final Notifier notifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOfferCreated(TaskOfferCreatedEvent event) {
        taskOffers.findById(event.offerId())
                .ifPresent(offer -> notifier.rider(event.agentId(), EventType.OFFER, routePlanner.toOfferDto(offer)));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOfferWithdrawn(OfferWithdrawnEvent event) {
        notifier.rider(event.agentId(), EventType.OFFER_WITHDRAWN,
                new OfferWithdrawn(event.offerId(), event.orderId(), event.reason()));
    }

    /** The "+1 stop" haptic flash with the bonus and the rider's updated route. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRouteInjected(RouteInjectedEvent event) {
        injections.findById(event.injectionId()).ifPresent(injection -> routes.findById(injection.getRouteId()).ifPresent(route -> {
            var notice = new RouteInjectionNotice(injection.getId(),
                    "Route Optimized: +1 Stop added nearby (+₹" + injection.getBonus().stripTrailingZeros().toPlainString() + " earned)",
                    injection.getBonus(), routePlanner.toResponse(route, stops.findByRouteIdOrderBySequence(route.getId())));
            notifier.rider(event.agentId(), EventType.STOP_ADDED, notice);
        }));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onRouteChanged(RouteChangedEvent event) {
        routes.findByAgentIdAndStatus(event.agentId(), RiderRoute.Status.ACTIVE).ifPresentOrElse(
                route -> notifier.rider(event.agentId(), EventType.ROUTE_UPDATED,
                        routePlanner.toResponse(route, stops.findByRouteIdOrderBySequence(route.getId()))),
                () -> notifier.rider(event.agentId(), EventType.ROUTE_UPDATED, RoutePlanner.emptyRoute()));
    }
}
