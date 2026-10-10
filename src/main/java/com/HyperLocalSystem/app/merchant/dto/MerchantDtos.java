package com.HyperLocalSystem.app.merchant.dto;

import com.HyperLocalSystem.app.auth.dto.AuthDtos;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.common.api.EarningsPeriod;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.api.PayoutItem;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.MerchantDocument;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.security.PhoneNumbers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
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
            String imageUrl,
            List<String> cuisines,
            String openingHours,
            String upiVpa,
            Instant createdAt) {
    }

    /** Partial update: null fields are left unchanged. Location / address changes go through support. */
    public record UpdateMerchantProfileRequest(
            @Size(min = 1, max = 120) String storeName,
            @Size(min = 1, max = 100) String ownerName,
            @Size(max = 500) String imageUrl,
            @Size(max = 100) String openingHours,
            @Size(max = 5) List<@NotBlank @Size(max = 30) String> cuisines,
            @Size(max = 100) String upiVpa) {
    }

    // ----------------------------------------------------------------- uploads & documents

    /** DOCUMENT: private (owner + admin). STORE_PHOTO / ITEM_PHOTO: publicly readable (customer catalogue). */
    public enum UploadPurpose { DOCUMENT, STORE_PHOTO, ITEM_PHOTO }

    public record UploadRequest(
            @NotNull UploadPurpose purpose,
            @NotBlank @Pattern(regexp = "^(image/(jpeg|png)|application/pdf)$") String contentType) {
    }

    /** PUT the raw file to {@code uploadUrl}, then save {@code fileUrl} where it belongs. */
    public record UploadResponse(String uploadUrl, String fileKey, String fileUrl, boolean publicRead, Instant expiresAt) {
    }

    public record MerchantDocumentRequest(
            @Size(max = 30) String documentNumber,
            @NotBlank @Size(max = 500) String fileUrl) {
    }

    public record MerchantDocumentResponse(
            MerchantDocument.Type type,
            String documentNumberMasked,
            String fileUrl,
            MerchantDocument.Status status,
            String remarks,
            Instant uploadedAt) {
    }

    public record MerchantDocumentsResponse(
            ApprovalStatus verificationStatus,
            Set<MerchantDocument.Type> missingDocuments,
            List<MerchantDocumentResponse> documents) {
    }

    public record StoreStatusRequest(@NotNull Boolean open) {
    }

    // ----------------------------------------------------------------- Kirana broadcast

    /** {@code itemId}: the order line (stable key for a packing checklist); {@code zone}: HOT / COLD / AMBIENT. */
    public record BroadcastItem(String name, String packSize, int quantity, UUID itemId, TemperatureZone zone) {
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

    /** Same style as the partner earnings: settled orders and their total for the period, and the payouts. */
    public record MerchantEarnings(
            EarningsPeriod period,
            LocalDate from,
            LocalDate to,
            int orderCount,
            BigDecimal total,
            PageResponse<PayoutItem> payouts) {
    }

    // ----------------------------------------------------------------- live events (data of /user/queue/merchant-events)

    public record OrderCancelledNotice(UUID orderId, String reason) {
    }

    /** {@code reason}: CLAIMED (another shop was faster), EXPIRED or CANCELLED. */
    public record BroadcastTakenNotice(UUID broadcastId, UUID orderId, String reason) {
    }

    public record RiderArrivingNotice(UUID orderId, UUID stopId, String riderName, String riderPhone,
                                      String vehicleNumber, Instant arrivedAt) {
    }

    public record OrderPickedUpNotice(UUID orderId, UUID stopId, Instant pickedUpAt) {
    }

    // ----------------------------------------------------------------- orders

    /**
     * An order card. {@code shortCode}: last 4 hex digits of the order id, upper case, for "#A3F9" on the
     * card and at the counter; {@code createdAt} is when the customer placed it.
     */
    public record MerchantOrder(
            UUID orderId,
            MerchantOrderStatus status,
            List<BroadcastItem> items,
            BigDecimal amount,
            Instant prepDeadline,
            String riderName,
            Instant riderEta,
            String customerNote,
            Instant createdAt,
            String shortCode) {

        public static String shortCode(UUID orderId) {
            String hex = orderId.toString().replace("-", "");
            return hex.substring(hex.length() - 4).toUpperCase(java.util.Locale.ROOT);
        }
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

    /**
     * Restaurants add menu items (FOOD), Kiranas add shelf products (GROCERY); the type follows the
     * store. {@code brand}, {@code packSize} and {@code mrp} are for groceries.
     */
    public record CatalogItemRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotBlank @Size(max = 60) String category,
            Boolean veg,
            @NotNull TemperatureZone zone,
            @Size(max = 500) String imageUrl,
            boolean available,
            @Size(max = 60) String brand,
            @Size(max = 30) String packSize,
            @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 8, fraction = 2) BigDecimal mrp) {
    }

    public record CatalogItemResponse(
            UUID id,
            ItemType itemType,
            String name,
            String description,
            BigDecimal price,
            String category,
            Boolean veg,
            TemperatureZone zone,
            String imageUrl,
            boolean available,
            String brand,
            String packSize,
            BigDecimal mrp) {
    }

    public record AvailabilityRequest(@NotNull Boolean available) {
    }
}
