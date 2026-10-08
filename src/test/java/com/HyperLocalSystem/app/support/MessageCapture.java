package com.HyperLocalSystem.app.support;

import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only: listens on Spring's broker channel and keeps every message sent to a user
 * destination, so tests can assert on WebSocket traffic without opening a socket.
 */
public class MessageCapture implements MessageHandler {

    public record Captured(String destination, String json) {
    }

    private final List<Captured> messages = new CopyOnWriteArrayList<>();

    @Override
    public void handleMessage(Message<?> message) {
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination != null && destination.startsWith("/user/") && message.getPayload() instanceof byte[] body) {
            messages.add(new Captured(destination, new String(body, StandardCharsets.UTF_8)));
        }
    }

    /** Waits for a message of {@code type} on the user's queue (e.g. "/queue/tracking") and returns its JSON. */
    public String waitFor(UUID userId, String queue, String type) {
        return waitFor(userId, queue, type, "$.type", type);
    }

    /** Like {@link #waitFor(UUID, String, String)}, but the message must also have {@code path == expected}. */
    public String waitFor(UUID userId, String queue, String type, String path, Object expected) {
        String destination = "/user/" + userId + queue;
        String[] found = new String[1];
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100)).until(() -> {
            Optional<Captured> hit = messages.stream()
                    .filter(m -> m.destination().equals(destination) && type.equals(JsonPath.read(m.json(), "$.type"))
                            && matches(m.json(), path, expected))
                    .findFirst();
            hit.ifPresent(m -> found[0] = m.json());
            return hit.isPresent();
        });
        return found[0];
    }

    private static boolean matches(String json, String path, Object expected) {
        try {
            return expected.equals(JsonPath.read(json, path));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public List<String> all(UUID userId, String queue) {
        String destination = "/user/" + userId + queue;
        return messages.stream().filter(m -> m.destination().equals(destination)).map(Captured::json).toList();
    }
}
