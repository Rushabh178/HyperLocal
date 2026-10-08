package com.HyperLocalSystem.app.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Firebase Cloud Messaging ("fcm" profile). Credentials: the service-account JSON at
 * {@code FIREBASE_CREDENTIALS}, else Google application-default credentials
 * ({@code GOOGLE_APPLICATION_CREDENTIALS}).
 * <p>
 * The data payload is the WebSocket envelope as strings: {@code type}, {@code sentAt} and
 * {@code data} (JSON). Offers are data-only and high priority, with a TTL that ends at the offer's
 * {@code expiresAt}; everything else also carries a notification title and body.
 */
@Slf4j
@Component
@Profile("fcm")
public class FcmPushSender implements PushSender {

    private final FirebaseMessaging messaging;
    private final ObjectMapper objectMapper;

    public FcmPushSender(@Value("${FIREBASE_CREDENTIALS:}") String credentialsPath, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            GoogleCredentials credentials;
            if (credentialsPath.isBlank()) {
                credentials = GoogleCredentials.getApplicationDefault();
            } else {
                try (InputStream in = new FileInputStream(credentialsPath)) {
                    credentials = GoogleCredentials.fromStream(in);
                }
            }
            FirebaseApp app = FirebaseApp.getApps().stream().filter(a -> a.getName().equals("hyperlocal")).findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build(), "hyperlocal"));
            this.messaging = FirebaseMessaging.getInstance(app);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load Firebase credentials", e);
        }
    }

    @Override
    public boolean send(String deviceToken, PushMessage push) {
        Message.Builder message = Message.builder()
                .setToken(deviceToken)
                .putData("type", push.envelope().type().name())
                .putData("sentAt", push.envelope().sentAt().toString())
                .putData("data", objectMapper.writeValueAsString(push.envelope().data()));
        if (push.isDataOnly()) {
            long ttlMillis = push.expiresAt() == null ? 0 : Math.max(0, Duration.between(Instant.now(), push.expiresAt()).toMillis());
            message.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).setTtl(ttlMillis).build());
            // iOS only accepts background (content-available) pushes at priority 5.
            ApnsConfig.Builder apns = ApnsConfig.builder()
                    .putHeader("apns-push-type", "background")
                    .putHeader("apns-priority", "5")
                    .setAps(Aps.builder().setContentAvailable(true).build());
            if (push.expiresAt() != null) {
                apns.putHeader("apns-expiration", Long.toString(push.expiresAt().getEpochSecond()));
            }
            message.setApnsConfig(apns.build());
        } else {
            message.setNotification(Notification.builder().setTitle(push.title()).setBody(push.body()).build());
        }
        try {
            messaging.send(message.build());
            return true;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return false;
            }
            log.warn("FCM push {} failed: {}", push.envelope().type(), e.getMessage());
            // TODO(edge-case): retry transient FCM errors (UNAVAILABLE / QUOTA_EXCEEDED) with backoff.
            return true;
        }
    }
}
