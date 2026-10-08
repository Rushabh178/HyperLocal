package com.HyperLocalSystem.app.realtime;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RouteInjectionNotice;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Server → app pushes. User destinations are keyed by the JWT subject (customer_id / agent_id). */
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    private final SimpMessagingTemplate messaging;

    public void sendTracking(UUID customerId, TrackingResponse update) {
        messaging.convertAndSendToUser(customerId.toString(), "/queue/tracking", update);
    }

    /** Triggers the "Haptic Injection Flash" in the rider app. */
    public void sendRouteInjection(UUID agentId, RouteInjectionNotice notice) {
        messaging.convertAndSendToUser(agentId.toString(), "/queue/route-updates", notice);
    }
}
