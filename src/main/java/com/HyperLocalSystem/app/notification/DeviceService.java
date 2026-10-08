package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.notification.entity.DeviceToken;
import com.HyperLocalSystem.app.notification.repository.DeviceTokenRepository;
import com.HyperLocalSystem.app.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Push tokens of signed-in phones. */
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceTokenRepository devices;

    /** Upsert on the token: the token moves to whoever registered it last on that phone. */
    @Transactional
    public void register(UUID userId, Role role, String token, DeviceToken.Platform platform) {
        DeviceToken device = devices.findByToken(token.trim()).orElseGet(DeviceToken::new);
        device.setToken(token.trim());
        device.setUserId(userId);
        device.setRole(role);
        device.setPlatform(platform);
        devices.save(device);
        // TODO(edge-case): two registrations of a brand-new token at the same instant race on the unique token (one gets 409).
    }

    /** Logout: this phone stops receiving pushes for the account. */
    @Transactional
    public void unregister(String token, Role role) {
        if (token != null && !token.isBlank()) {
            devices.deleteByTokenAndRole(token.trim(), role);
        }
    }

    /** The push service said the token is dead (app uninstalled / token rotated). */
    @Transactional
    public void forget(String token) {
        devices.deleteByToken(token);
    }

    @Transactional(readOnly = true)
    public List<String> tokensOf(UUID userId, Role role) {
        return devices.findByUserIdAndRole(userId, role).stream().map(DeviceToken::getToken).toList();
    }
}
