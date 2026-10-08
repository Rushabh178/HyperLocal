package com.HyperLocalSystem.app.notification.push;

/**
 * Delivers a push to one device. {@link LoggingPushSender} in development, {@link FcmPushSender}
 * with the "fcm" profile.
 */
public interface PushSender {

    /** @return false if the token is dead (uninstalled app, rotated token) and should be forgotten */
    boolean send(String deviceToken, PushMessage message);
}
