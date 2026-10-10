package com.HyperLocalSystem.app.agent.dto;

import com.HyperLocalSystem.app.common.api.EarningsPeriod;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.api.PayoutItem;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AgentTaskDtos {

    private AgentTaskDtos() {
    }

    /** A new order offered to an AVAILABLE partner. Must be accepted before {@code expiresAt}. */
    public record TaskOffer(
            UUID offerId,
            List<UUID> orderIds,
            List<RouteStop> stops,
            int totalDistanceMeters,
            BigDecimal estimatedEarnings,
            Instant expiresAt) {
    }

    public record StopItem(UUID itemId, String name, int quantity, TemperatureZone zone, boolean deficient) {
    }

    public record RouteStop(
            UUID stopId,
            int sequence,
            StopType type,
            String name,
            String address,
            GeoPoint location,
            List<UUID> orderIds,
            List<StopItem> items,
            StopState state,
            Instant eta) {
    }

    /**
     * The partner's current linear route. {@code locked} = the navigation sequence is controlled by
     * the server (Linear Route Locking); the app must not reorder stops.
     */
    public record ActiveRouteResponse(
            UUID routeId,
            boolean locked,
            List<RouteStop> stops,
            int remainingDistanceMeters,
            Instant estimatedCompletionAt) {
    }

    public record RejectOfferRequest(@Size(max = 100) String reason) {
    }

    /** Payload of the store's QR (Kirana or restaurant); scanning it completes the pickup. */
    public record KiranaQrScanRequest(@NotBlank @Size(max = 512) String qrPayload) {
    }

    public record MarkDeficientRequest(@Size(max = 100) String reason) {
    }

    /** Items (stop item ids) the Kirana doesn't have. */
    public record MarkMissingRequest(@NotEmpty List<@NotNull UUID> itemIds, @Size(max = 100) String reason) {
    }

    /** 4-digit code the customer reads out at the door. */
    public record DeliverRequest(@NotBlank @Pattern(regexp = "^\\d{4}$") String deliveryOtp) {
    }

    /**
     * Pushed on /user/queue/route-updates when the batching engine injects a stop into this route.
     * The app plays the haptic pattern and flashes "Route Optimized: +1 Stop added nearby (+₹15 earned)".
     */
    public record RouteInjectionNotice(
            UUID injectionId,
            String message,
            BigDecimal bonusAmount,
            ActiveRouteResponse updatedRoute) {
    }

    public record DeliveryHistoryItem(
            UUID orderId,
            boolean batched,
            int stops,
            BigDecimal earnings,
            Instant completedAt) {
    }

    /**
     * Single base payout per trip plus multi-stop / batching incentive bonuses, in the same style as
     * the merchant earnings. {@code onlineMinutes} is always for today.
     */
    public record EarningsSummary(
            EarningsPeriod period,
            LocalDate from,
            LocalDate to,
            int tripCount,
            int batchedInjections,
            BigDecimal basePay,
            BigDecimal multiStopBonus,
            BigDecimal incentives,
            BigDecimal total,
            long onlineMinutes,
            PageResponse<PayoutItem> payouts,
            /** Per-km pay beyond 2 km of route (already in {@code total}). */
            BigDecimal distancePay) {
    }
}
