package com.HyperLocalSystem.app.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Server → app WebSocket pushes, always wrapped in {@link RealtimeMessage}. User destinations are
 * keyed by the JWT subject (customer_id / agent_id / merchant_id). Callers go through
 * {@code notification.Notifier}, which also sends the matching push notification.
 */
@Component
@RequiredArgsConstructor
public class RealtimePublisher {

    public static final String CUSTOMER_QUEUE = "/queue/tracking";
    public static final String RIDER_QUEUE = "/queue/route-updates";
    public static final String MERCHANT_QUEUE = "/queue/merchant-events";

    private final SimpMessagingTemplate messaging;

    public void send(UUID userId, String queue, RealtimeMessage message) {
        messaging.convertAndSendToUser(userId.toString(), queue, message);
    }
}
