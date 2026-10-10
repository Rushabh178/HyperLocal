package com.HyperLocalSystem.app.order.dto;

import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartItemResponse;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.customer.dto.CustomerDtos.AddressResponse;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import com.HyperLocalSystem.app.payment.entity.PaymentMethod;
import com.HyperLocalSystem.app.payment.entity.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    /** Converts the current cart into one order. Send an {@code Idempotency-Key} header to make retries safe. */
    public record CheckoutRequest(
            @NotNull UUID addressId,
            @NotNull PaymentMethod paymentMethod,
            /** Optional note for the stores ("less spicy", "ring the bell"), shown on their order card. */
            @Size(max = 250) String note) {
    }

    /**
     * {@code upiIntentUrl}: open it in the UPI app. Null when there is nothing to approve (already
     * CONFIRMED by the fake gateway or a wallet payment) or the hold failed; then wait for
     * PAYMENT_CONFIRMED / PAYMENT_FAILED on /user/queue/tracking or poll GET /orders/{id}.
     */
    public record CheckoutResponse(UUID orderId, OrderStatus status, BigDecimal payableAmount, String upiIntentUrl,
                                   PaymentHoldResponse payment) {
    }

    /** Data of PAYMENT_CONFIRMED / PAYMENT_FAILED. */
    public record PaymentUpdate(UUID orderId, UUID paymentId, PaymentStatus paymentStatus, OrderStatus orderStatus,
                                BigDecimal amount, String message) {
    }

    public record OrderSummary(
            UUID orderId,
            OrderStatus status,
            boolean hasFood,
            boolean hasGroceries,
            BigDecimal total,
            boolean rated,
            Instant createdAt) {
    }

    /** The customer's rating of an order (stars 1-5; a part the order didn't have is null). */
    public record OrderRatingResponse(Integer deliveryRating, Integer restaurantRating, Integer kiranaRating, String comment,
                                      Instant createdAt) {
    }

    /** The rider carrying the order. {@code phone} is the full number for now. */
    public record RiderInfo(String name, String phone, String vehicleNumber, GeoPoint location) {
    }

    public record OrderDetail(
            UUID orderId,
            OrderStatus status,
            List<CartItemResponse> foodItems,
            List<CartItemResponse> groceryItems,
            BillSummary bill,
            List<MissingItem> missingItems,
            AddressResponse deliveryAddress,
            RiderInfo rider,
            String deliveryOtp,
            boolean cancellable,
            List<TrackingStop> stops,
            boolean rated,
            OrderRatingResponse rating,
            String note,
            Instant createdAt) {
    }

    /** How a stop looks to the customer. */
    public enum TrackingStopStatus { PENDING, ARRIVED, PICKED_UP, SKIPPED, DELIVERED }

    /**
     * {@code stopId} is null until a rider has the order (the stops are only planned then). {@code lat}/{@code lng}/
     * {@code address} are null only while no Kirana has claimed the groceries yet.
     */
    public record TrackingStop(UUID stopId, StopType type, String name, TrackingStopStatus status, Instant completedAt,
                               Double lat, Double lng, String address) {
    }

    /** An item the store didn't have; {@code refundAmount} goes back to the customer's wallet. */
    public record MissingItem(UUID itemId, String name, int quantity, BigDecimal refundAmount) {
    }

    /** STOPS_UPDATED message: the stop line plus the money side, so refunds show up live. */
    public record StopsUpdate(UUID orderId, OrderStatus status, List<TrackingStop> stops, List<MissingItem> missingItems,
                              BillSummary bill) {
    }

    /** GROCERIES_UNFULFILLED message: the groceries were dropped, {@code refundAmount} went to the wallet. */
    public record GroceriesUnfulfilled(UUID orderId, BigDecimal refundAmount, String reason, BillSummary bill) {
    }

    /** One icon on the unified timeline; icons light up in sequence as the single courier advances. */
    public record TimelineStop(StopType type, String name, StopState state, Instant eta, Instant completedAt) {
    }

    public record TimelineResponse(UUID orderId, OrderStatus status, List<TimelineStop> stops, Instant estimatedDeliveryAt) {
    }

    /** Snapshot for the first paint; live updates arrive on the WebSocket user queue /user/queue/tracking. */
    public record TrackingResponse(UUID orderId, OrderStatus status, GeoPoint agentLocation, Integer etaMinutes,
                                   List<TrackingStop> stops, Instant updatedAt) {
    }

    /** RIDER_LOCATION message: where the rider is and how far this order is. */
    public record RiderLocation(UUID orderId, GeoPoint location, Integer etaMinutes, Instant updatedAt) {
    }

    public record CancelOrderRequest(@NotBlank @Size(max = 250) String reason) {
    }

    public record RateOrderRequest(
            @Min(1) @Max(5) Integer deliveryRating,
            @Min(1) @Max(5) Integer restaurantRating,
            @Min(1) @Max(5) Integer kiranaRating,
            @Size(max = 500) String comment) {
    }
}
