package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.security.Hashing;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Signs and verifies the in-store QR the rider scans at a Kirana. Payload:
 * {@code HLQR1.<merchantId>.<expiresAtEpochSeconds>.<base64url HMAC-SHA256>}. Stateless, so any
 * instance can verify a QR another instance issued. Reuses the JWT key with its own message
 * prefix, so a QR signature can never be replayed as a token signature or the other way round.
 */
@Service
public class StoreQrService {

    static final Duration TTL = Duration.ofMinutes(15);
    private static final String PREFIX = "HLQR1";

    private final SecretKey key;

    public StoreQrService(SecretKey jwtSigningKey) {
        this.key = jwtSigningKey;
    }

    public record SignedQr(String payload, Instant expiresAt) {
    }

    public SignedQr issue(UUID merchantId) {
        Instant expiresAt = Instant.now().plus(TTL);
        String body = PREFIX + "." + merchantId + "." + expiresAt.getEpochSecond();
        return new SignedQr(body + "." + sign(body), expiresAt);
    }

    /** Returns the merchant the QR belongs to, or throws if it is forged or expired. */
    public UUID verify(String payload) {
        String[] parts = payload.trim().split("\\.");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            throw invalid();
        }
        String body = parts[0] + "." + parts[1] + "." + parts[2];
        if (!Hashing.constantTimeEquals(sign(body), parts[3])) {
            throw invalid();
        }
        UUID merchantId;
        long expiresAt;
        try {
            merchantId = UUID.fromString(parts[1]);
            expiresAt = Long.parseLong(parts[2]);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
            throw ApiException.badRequest("QR_EXPIRED", "This store QR has expired. Ask the shop to refresh it.");
        }
        return merchantId;
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update("store-qr|".getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("QR_INVALID", "This is not a valid store QR");
    }
}
