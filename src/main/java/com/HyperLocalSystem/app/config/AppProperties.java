package com.HyperLocalSystem.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/** Typed view of the {@code app.*} settings in application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Otp otp, Ops ops, Routing routing) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Otp(int length, Duration ttl, int maxAttempts, Duration resendCooldown, String provider) {
    }

    /** Operational constants from the architecture blueprint. */
    public record Ops(
            int serviceRadiusMeters,
            int batchingCorridorMeters,
            Duration batchingMaxAddedDelay,
            int kiranaBroadcastRadiusMeters,
            int kiranaBroadcastFanout,
            Duration kiranaClaimTimeout,
            int h3Resolution,
            BigDecimal groceryCartNudgeThreshold,
            BigDecimal batchInjectionBonus,
            /** Used for ETAs before a restaurant has accepted and set its own prep time. */
            int defaultPrepMinutes
    ) {
    }

    /**
     * Road routing. Without the "osrm" profile, ETAs use straight-line distance x {@code detourFactor}
     * at {@code averageSpeedKmph}.
     */
    public record Routing(double detourFactor, double averageSpeedKmph, String osrmBaseUrl) {
    }
}
