package com.HyperLocalSystem.app.catalog.service;

import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.GroceryItem;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuResponse;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.RestaurantSummary;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.SearchResponse;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CatalogService {

    /** TODO: open restaurants within ops.serviceRadiusMeters (2 km) of the pin, nearest first (ST_DWithin + ST_Distance). */
    public PageResponse<RestaurantSummary> nearbyRestaurants(double lat, double lng, String cuisine, int page, int size) {
        throw new NotImplementedException("Nearby restaurants");
    }

    /** TODO: menu grouped by category; hide unavailable items or flag them. */
    public MenuResponse menu(UUID restaurantId) {
        throw new NotImplementedException("Restaurant menu");
    }

    /** TODO: virtual-warehouse catalogue, shown only when at least one Kirana is within 2 km of the pin. */
    public PageResponse<GroceryItem> groceries(double lat, double lng, String query, String category, int page, int size) {
        throw new NotImplementedException("Grocery catalogue");
    }

    public SearchResponse search(double lat, double lng, String query) {
        throw new NotImplementedException("Unified search");
    }
}
