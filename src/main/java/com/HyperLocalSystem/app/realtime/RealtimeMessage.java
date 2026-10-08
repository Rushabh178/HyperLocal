package com.HyperLocalSystem.app.realtime;

import java.time.Instant;

/**
 * The one envelope on every WebSocket topic: {@code {"type":"OFFER","sentAt":"...","data":{...}}}.
 * Push notifications carry the same three fields in their data payload.
 */
public record RealtimeMessage(EventType type, Instant sentAt, Object data) {

    public static RealtimeMessage of(EventType type, Object data) {
        return new RealtimeMessage(type, Instant.now(), data);
    }
}
