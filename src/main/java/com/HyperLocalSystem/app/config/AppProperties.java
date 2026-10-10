package com.HyperLocalSystem.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/** Typed view of the {@code app.*} settings in application.properties. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Otp otp, Ops ops, Routing routing, Payment payment, Admin admin, Storage storage,
                            Delivery delivery, Rider rider) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Otp(int length, Duration ttl, int maxAttempts, Duration resendCooldown, String provider) {
    }

    /** Operational constants from the architecture blueprint. */
    public record Ops(
            int batchingCorridorMeters,
            Duration batchingMaxAddedDelay,
            int kiranaBroadcastRadiusMeters,
            int kiranaBroadcastFanout,
            Duration kiranaClaimTimeout,
            /** Rounds before the grocery part is marked UNFULFILLED; each round widens the radius. */
            int kiranaBroadcastMaxRounds,
            int h3Resolution,
            BigDecimal groceryCartNudgeThreshold,
            BigDecimal batchInjectionBonus,
            /** Used for ETAs before a restaurant has accepted and set its own prep time. */
            int defaultPrepMinutes,
            /** Charged unless the cart has food or groceries reach groceryCartNudgeThreshold. */
            BigDecimal deliveryFee,
            /** One flat platform fee per order, whatever it contains. */
            BigDecimal convenienceFee,
            /** GST on restaurant food, e.g. 0.05. Groceries are priced tax-inclusive (MRP). */
            BigDecimal foodTaxRate,
            /** Combined orders: wait this long before broadcasting, so groceries aren't picked up long before the food. */
            Duration kiranaBroadcastCombinedDelay,
            /** How far from the first pickup to look for a free rider. */
            int riderSearchRadiusMeters,
            /** A rider must accept an offer within this time, else it goes to the next rider. */
            Duration riderOfferTimeout,
            /** Paid per delivered order. */
            BigDecimal riderBasePay,
            /** Paid per extra pickup on the same order (Kirana + restaurant = 1 extra). */
            BigDecimal riderMultiStopBonus
    ) {
    }

    /**
     * Delivery range and pricing, see {@code delivery.DeliveryPricing}. Radii are straight-line km from the
     * delivery address; {@code freeDeliveryKm} and {@code feeSlabs} ("2-3:15,3-4:25") are road km.
     */
    public record Delivery(double restaurantRadiusKm, double kiranaRadiusKm, double freeDeliveryKm, String feeSlabs,
                           double mealNudgeRadiusKm) {
    }

    /** Rider distance pay: {@code perKmRate} for every km of an order's route beyond {@code distancePayAfterKm}. */
    public record Rider(BigDecimal perKmRate, double distancePayAfterKm) {
    }

    /**
     * Road routing. Without the "osrm" profile, ETAs use straight-line distance x {@code detourFactor}
     * at {@code averageSpeedKmph}.
     */
    public record Routing(double detourFactor, double averageSpeedKmph, String osrmBaseUrl) {
    }

    /** {@code webhookSecret} verifies gateway callbacks (HMAC-SHA256); {@code holdTtl} is how long a UPI hold lasts. */
    public record Payment(String webhookSecret, Duration holdTtl, Razorpay razorpay) {
    }

    /** Used only with the "razorpay" profile. */
    public record Razorpay(String baseUrl, String keyId, String keySecret) {
    }

    /** First ops admin, created on startup when the email is set (ADMIN_EMAIL / ADMIN_PASSWORD / ADMIN_NAME). */
    public record Admin(String seedEmail, String seedPassword, String seedName) {
    }

    /** Local-disk file storage (dev default): where files go and the public URL prefix used in links. */
    public record Storage(String localDir, String publicBaseUrl) {
    }
}
