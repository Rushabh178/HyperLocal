package com.HyperLocalSystem.app.catalog.controller;

import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.GroceryCategory;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.GroceryItem;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuResponse;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.RestaurantSummary;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.SearchResponse;
import com.HyperLocalSystem.app.catalog.service.CatalogService;
import com.HyperLocalSystem.app.common.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public browsing (no sign-in needed). All discovery is limited to the 2 km operating radius. */
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/restaurants")
    public PageResponse<RestaurantSummary> restaurants(@RequestParam double lat, @RequestParam double lng,
                                                       @RequestParam(required = false) String cuisine,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return catalogService.nearbyRestaurants(lat, lng, cuisine, page, size);
    }

    @GetMapping("/restaurants/{restaurantId}/menu")
    public MenuResponse menu(@PathVariable UUID restaurantId) {
        return catalogService.menu(restaurantId);
    }

    @GetMapping("/groceries")
    public PageResponse<GroceryItem> groceries(@RequestParam double lat, @RequestParam double lng,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return catalogService.groceries(lat, lng, q, category, page, size);
    }

    @GetMapping("/groceries/categories")
    public PageResponse<GroceryCategory> groceryCategories(@RequestParam double lat, @RequestParam double lng,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return catalogService.groceryCategories(lat, lng, page, size);
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam double lat, @RequestParam double lng, @RequestParam String q) {
        return catalogService.search(lat, lng, q);
    }
}
