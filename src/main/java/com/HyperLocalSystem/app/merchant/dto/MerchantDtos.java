package com.HyperLocalSystem.app.merchant.dto;

import com.HyperLocalSystem.app.auth.dto.AuthDtos;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Kirana shops and restaurants share one merchant app; {@code type} decides which screens they see. */
public final class MerchantDtos {

    private MerchantDtos() {
    }

    public enum MerchantOrderStatus { NEW, ACCEPTED, PREPARING, DELAYED, READY, PICKED_UP, CANCELLED }

    // ----------------------------------------------------------------- sign up / profile

    /** Zero sign-up fee onboarding. FSSAI is mandatory for restaurants. */
    public record MerchantSignupRequest(
            @NotNull MerchantType type,
            @NotBlank @Size(max = 120) String storeName,
            @NotBlank @Size(max = 100) String ownerName,
            @NotBlank @Pattern(regexp = PhoneNumbers.INDIAN_MOBILE_REGEX, message = AuthDtos.PHONE_MESSAGE) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Pattern(regexp = "^[1-9]\\d{5}$", message = "must be a 6-digit PIN code") String pincode,
            @NotNull @Valid GeoPoint location,
            @Size(max = 14) String fssaiNumber,
            @Size(max = 15) String gstin,
            @Size(max = 100) String upiVpa,
            /** Restaurants only, e.g. ["North Indian", "Chinese"]. */
            @Size(max = 5) List<@NotBlank @Size(max = 30) String> cuisines,
            @Size(max = 500) String imageUrl) {
    }

    public record MerchantProfileResponse(
            UUID id,
            MerchantType type,
            String storeName,
            String ownerName,
            String phone,
            String address,
            GeoPoint location,
            boolean open,
            ApprovalStatus verificationStatus,
            String rejectionReason,
            Instant createdAt) {
    }

    public record StoreStatusRequest(@NotNull Boolean open) {
    }

    // ----------------------------------------------------------------- Kirana broadcast

    public record BroadcastItem(String name, String packSize, int quantity) {
    }

    /** A ping sent to the 5 nearest Kiranas. First to tap "Items in Hand" wins. */
    public record KiranaBroadcast(
            UUID broadcastId,
            UUID orderId,
            List<BroadcastItem> items,
            BigDecimal payoutAmount,
            int distanceMeters,
            Instant expiresAt) {
    }

    public record ClaimResponse(UUID broadcastId, UUID orderId, boolean claimed, String message) {
    }

    // ----------------------------------------------------------------- orders

    public record MerchantOrder(
            UUID orderId,
            MerchantOrderStatus status,
            List<BroadcastItem> items,
            BigDecimal amount,
            Instant prepDeadline,
            String riderName,
            Instant riderEta) {
    }

    public record AcceptOrderRequest(@Min(1) @Max(90) int prepMinutes) {
    }

    /** Kitchen bottleneck: the Kirana dispatch is held back so cold items don't wait at the counter. */
    public record PrepDelayRequest(@Min(1) @Max(60) int additionalMinutes, @Size(max = 150) String reason) {
    }

    /** Signed, short-lived payload rendered as the in-store QR the rider scans. */
    public record StoreQrResponse(String qrPayload, Instant expiresAt) {
    }

    // ----------------------------------------------------------------- menu / items

    public record CatalogItemRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @NotBlank @Size(max = 60) String category,
            Boolean veg,
            @NotNull TemperatureZone zone,
            @Size(max = 500) String imageUrl,
            boolean available) {
    }

    public record CatalogItemResponse(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            String category,
            Boolean veg,
            TemperatureZone zone,
            String imageUrl,
            boolean available) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }
}
