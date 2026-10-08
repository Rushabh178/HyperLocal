package com.HyperLocalSystem.app.support;

import com.HyperLocalSystem.app.notification.push.PushMessage;
import com.HyperLocalSystem.app.notification.push.PushSender;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test-only PushSender that records every push so tests can assert on them. */
public class CapturingPushSender implements PushSender {

    public record Sent(String token, PushMessage message) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public boolean send(String deviceToken, PushMessage message) {
        sent.add(new Sent(deviceToken, message));
        return true;
    }

    /** Waits for a push of {@code type} (EventType name) to {@code token}. */
    public PushMessage waitFor(String token, String type) {
        PushMessage[] found = new PushMessage[1];
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            Optional<Sent> hit = sent.stream()
                    .filter(s -> s.token().equals(token) && s.message().envelope().type().name().equals(type))
                    .findFirst();
            hit.ifPresent(s -> found[0] = s.message());
            return hit.isPresent();
        });
        return found[0];
    }

    public long count(String token) {
        return sent.stream().filter(s -> s.token().equals(token)).count();
    }
}
