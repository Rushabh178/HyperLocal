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
            int distanceMeters,
            int etaMinutes,
            boolean open,
            String imageUrl) {
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

    public record MenuResponse(UUID restaurantId, String restaurantName, boolean open, List<MenuCategory> categories) {
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

    public record SearchResponse(
            List<RestaurantSummary> restaurants,
            List<MenuItem> dishes,
            List<GroceryItem> groceries) {
    }
}
