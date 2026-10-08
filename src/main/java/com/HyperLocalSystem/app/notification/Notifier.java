package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.realtime.EventType;
import com.HyperLocalSystem.app.realtime.RealtimeMessage;
import com.HyperLocalSystem.app.realtime.RealtimePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single way to tell an app something happened: one {@link RealtimeMessage} on the user's
 * WebSocket queue. Only called from after-commit event listeners, never from controllers.
 */
@Component
@RequiredArgsConstructor
public class Notifier {

    private final RealtimePublisher realtime;

    public void customer(UUID customerId, EventType type, Object data) {
        realtime.send(customerId, RealtimePublisher.CUSTOMER_QUEUE, RealtimeMessage.of(type, data));
    }

    public void rider(UUID agentId, EventType type, Object data) {
        realtime.send(agentId, RealtimePublisher.RIDER_QUEUE, RealtimeMessage.of(type, data));
    }

    public void merchant(UUID merchantId, EventType type, Object data) {
        realtime.send(merchantId, RealtimePublisher.MERCHANT_QUEUE, RealtimeMessage.of(type, data));
    }
}
