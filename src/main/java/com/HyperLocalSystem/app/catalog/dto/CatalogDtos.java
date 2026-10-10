package com.HyperLocalSystem.app.catalog.dto;

import com.HyperLocalSystem.app.common.model.TemperatureZone;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record RestaurantSummary(
            UUID id,
            String name,
            List<String> cuisines,
            /** Average stars to one decimal (not money), null until rated. */
            Double rating,
            /** Straight line from the customer's pin (what decides visibility). */
            int distanceMeters,
            int etaMinutes,
            boolean open,
            String imageUrl,
            /** Km from the restaurant to the pin on {@code fee-distance-basis}, one decimal: what the delivery fee is based on. */
            double distanceKm,
            /** 0.00 = "Free delivery"; otherwise the distance slab. */
            BigDecimal deliveryFee) {
    }

    public record MenuItem(
            UUID id,
            String name,
            String description,
            BigDecimal price,
            boolean veg,
            boolean available,
            String imageUrl) {
    }

    public record MenuCategory(String name, List<MenuItem> items) {
    }

    /**
     * A restaurant's menu. {@code isOpen} repeats {@code open} under the name the apps use. The
     * distance (fee distance, km), ETA and delivery fee need the customer's pin ({@code ?lat&lng}) and are null
     * without it; {@code rating} is null until rated.
     */
    public record MenuResponse(
            UUID restaurantId,
            String restaurantName,
            boolean open,
            boolean isOpen,
            Double distanceKm,
            Integer etaMinutes,
            Double rating,
            int ratingCount,
            BigDecimal deliveryFee,
            List<MenuCategory> categories) {
    }

    /**
     * An item of the "virtual warehouse": the platform-wide grocery catalogue. Stock is not
     * tracked per shop; availability is confirmed by a Kirana claiming the broadcast.
     */
    public record GroceryItem(
            UUID id,
            String name,
            String brand,
            String packSize,
            BigDecimal mrp,
            BigDecimal price,
            String category,
            TemperatureZone zone,
            String imageUrl) {
    }

    /** A grocery category nearby and how many distinct products it has. */
    public record GroceryCategory(String category, long itemCount) {
    }

    /** A dish in search results, with the restaurant needed to add it to the cart ({@code id} = {@code itemId}). */
    public record DishResult(
            UUID id,
            UUID itemId,
            UUID restaurantId,
            String restaurantName,
            String name,
            String description,
            BigDecimal price,
            boolean veg,
            boolean available,
            String imageUrl) {
    }

    public record SearchResponse(
            List<RestaurantSummary> restaurants,
            List<DishResult> dishes,
            List<GroceryItem> groceries) {
    }
}
