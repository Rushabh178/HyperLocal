package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.notification.push.PushContent;
import com.HyperLocalSystem.app.notification.push.PushSender;
import com.HyperLocalSystem.app.realtime.EventType;
import com.HyperLocalSystem.app.realtime.RealtimeMessage;
import com.HyperLocalSystem.app.realtime.RealtimePublisher;
import com.HyperLocalSystem.app.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single way to tell an app something happened: one {@link RealtimeMessage} on the user's
 * WebSocket queue, and for the types {@link PushContent} lists, the same envelope as a push to every
 * phone the user is signed in on. Only called from after-commit event listeners, never from controllers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Notifier {

    private final RealtimePublisher realtime;
    private final DeviceService devices;
    private final PushSender pushSender;

    public void customer(UUID customerId, EventType type, Object data) {
        send(customerId, Role.CUSTOMER, RealtimePublisher.CUSTOMER_QUEUE, RealtimeMessage.of(type, data));
    }

    public void rider(UUID agentId, EventType type, Object data) {
        send(agentId, Role.DELIVERY_AGENT, RealtimePublisher.RIDER_QUEUE, RealtimeMessage.of(type, data));
    }

    public void merchant(UUID merchantId, EventType type, Object data) {
        send(merchantId, Role.MERCHANT, RealtimePublisher.MERCHANT_QUEUE, RealtimeMessage.of(type, data));
    }

    private void send(UUID userId, Role role, String queue, RealtimeMessage message) {
        realtime.send(userId, queue, message);
        PushContent.of(message).ifPresent(push -> {
            for (String token : devices.tokensOf(userId, role)) {
                try {
                    if (!pushSender.send(token, push)) {
                        devices.forget(token);
                    }
                } catch (RuntimeException e) {
                    log.warn("Push {} to {} {} failed: {}", message.type(), role, userId, e.getMessage());
                }
            }
        });
    }
}
