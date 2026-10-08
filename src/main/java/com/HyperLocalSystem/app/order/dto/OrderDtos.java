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
            @Size(max = 100) String upiVpa) {
    }

    public record CheckoutResponse(UUID orderId, OrderStatus status, BigDecimal payableAmount, PaymentHoldResponse payment) {
    }

    public record OrderSummary(
            UUID orderId,
            OrderStatus status,
            boolean hasFood,
            boolean hasGroceries,
            BigDecimal total,
            Instant createdAt) {
    }

    public record AgentInfo(String name, String maskedPhone, String vehicleNumber, GeoPoint location) {
    }

    public record OrderDetail(
            UUID orderId,
            OrderStatus status,
            List<CartItemResponse> foodItems,
            List<CartItemResponse> groceryItems,
            BillSummary bill,
            AddressResponse deliveryAddress,
            AgentInfo agent,
            String deliveryOtp,
            Instant createdAt) {
    }

    /** One icon on the unified timeline; icons light up in sequence as the single courier advances. */
    public record TimelineStop(StopType type, String name, StopState state, Instant eta, Instant completedAt) {
    }

    public record TimelineResponse(UUID orderId, OrderStatus status, List<TimelineStop> stops, Instant estimatedDeliveryAt) {
    }

    /** Snapshot for the first paint; live updates arrive on the WebSocket user queue /user/queue/tracking. */
    public record TrackingResponse(UUID orderId, OrderStatus status, GeoPoint agentLocation, Integer etaMinutes, Instant updatedAt) {
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
