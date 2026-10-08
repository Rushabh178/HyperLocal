package com.HyperLocalSystem.app.notification.push;

import com.HyperLocalSystem.app.realtime.RealtimeMessage;

import java.time.Instant;

/**
 * One push: the same {type, sentAt, data} envelope as the WebSocket, plus how to show it.
 * <ul>
 *   <li>{@code title}/{@code body} null → data-only, high-priority push that expires at
 *       {@code expiresAt} (offers: a stale offer must never arrive late).</li>
 *   <li>otherwise a normal notification with title and body.</li>
 * </ul>
 */
public record PushMessage(RealtimeMessage envelope, String title, String body, Instant expiresAt) {

    public static PushMessage dataOnly(RealtimeMessage envelope, Instant expiresAt) {
        return new PushMessage(envelope, null, null, expiresAt);
    }

    public static PushMessage notification(RealtimeMessage envelope, String title, String body) {
        return new PushMessage(envelope, title, body, null);
    }

    public boolean isDataOnly() {
        return title == null;
    }
}
