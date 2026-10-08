package com.HyperLocalSystem.app.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** DEVELOPMENT ONLY ("dev" profile, "fcm" off): logs pushes instead of sending them, so no Firebase credentials are needed. */
@Slf4j
@Component
@Profile("dev & !fcm")
public class LoggingPushSender implements PushSender {

    @Override
    public boolean send(String deviceToken, PushMessage message) {
        if (message.isDataOnly()) {
            log.info("[DEV PUSH] data-only {} to {} (expires {})", message.envelope().type(), abbreviate(deviceToken), message.expiresAt());
        } else {
            log.info("[DEV PUSH] {} to {}: {} / {}", message.envelope().type(), abbreviate(deviceToken), message.title(), message.body());
        }
        return true;
    }

    private static String abbreviate(String token) {
        return token.length() <= 12 ? token : token.substring(0, 12) + "…";
    }
}
