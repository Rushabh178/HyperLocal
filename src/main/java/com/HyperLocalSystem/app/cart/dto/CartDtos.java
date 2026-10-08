package com.HyperLocalSystem.app.cart.dto;

import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuItem;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One unified cart holds both food (one restaurant) and groceries. */
public final class CartDtos {

    private CartDtos() {
    }

    public enum CartItemType { FOOD, GROCERY }

    /** {@code restaurantId} is required for FOOD items. Adding food from another restaurant replaces the food section. */
    public record AddCartItemRequest(
            @NotNull CartItemType itemType,
            @NotNull UUID itemId,
            UUID restaurantId,
            @Min(1) @Max(20) int quantity) {
    }

    /** quantity 0 removes the line. */
    public record UpdateCartItemRequest(@Min(0) @Max(20) int quantity) {
    }

    public record SelectAddressRequest(@NotNull UUID addressId) {
    }

    public record CartItemResponse(
            UUID cartItemId,
            CartItemType itemType,
            UUID itemId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            TemperatureZone zone) {
    }

    /**
     * Single flat convenience fee and no grocery minimum, per the unit-economics model.
     * {@code refunds}: money already given back (orders only; 0.00 in the cart); {@code netTotal} = total - refunds.
     */
    public record BillSummary(
            BigDecimal foodSubtotal,
            BigDecimal grocerySubtotal,
            BigDecimal deliveryFee,
            BigDecimal convenienceFee,
            BigDecimal taxes,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal refunds,
            BigDecimal netTotal) {
    }

    /** Shown when a grocery-only cart is under the threshold (₹150): add a meal to unlock free delivery. */
    public record CartNudge(
            BigDecimal groceryTotal,
            BigDecimal threshold,
            String message,
            List<MenuItem> suggestedMeals) {
    }

    public record CartResponse(
            UUID restaurantId,
            List<CartItemResponse> foodItems,
            List<CartItemResponse> groceryItems,
            UUID deliveryAddressId,
            BillSummary bill,
            CartNudge nudge) {
    }
}
