package com.HyperLocalSystem.app.catalog.service;

import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.GroceryItem;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuCategory;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuItem;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.MenuResponse;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.RestaurantSummary;
import com.HyperLocalSystem.app.catalog.dto.CatalogDtos.SearchResponse;
import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.catalog.repository.CatalogItemRepository;
import com.HyperLocalSystem.app.catalog.repository.GroceryRow;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.common.model.TemperatureZone;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.merchant.repository.NearbyMerchant;
import com.HyperLocalSystem.app.routing.RoutingClient;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Public browsing. All discovery is limited to {@code ops.serviceRadiusMeters} (2 km) around the pin. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final int SEARCH_LIMIT = 10;

    private final MerchantRepository merchants;
    private final CatalogItemRepository items;
    private final SpatialIndexService spatialIndex;
    private final RoutingClient routingClient;
    private final AppProperties properties;

    /** Restaurants within 2 km, open ones first, then nearest. Closed ones are listed with {@code open=false}. */
    @Transactional(readOnly = true)
    public PageResponse<RestaurantSummary> nearbyRestaurants(double lat, double lng, String cuisine, int page, int size) {
        int radius = properties.ops().serviceRadiusMeters();
        Page<NearbyMerchant> rows = merchants.findNearby(spatialIndex.cellsWithin(lat, lng, radius),
                MerchantType.RESTAURANT.name(), lat, lng, radius, containsPattern(cuisine), null,
                PageResponse.request(page, size));
        List<RestaurantSummary> summaries = toSummaries(rows.getContent(), new GeoPoint(lat, lng));
        return new PageResponse<>(summaries, rows.getNumber(), rows.getSize(), rows.getTotalElements());
    }

    /** Full menu grouped by category; unavailable dishes are listed with {@code available=false}. */
    @Transactional(readOnly = true)
    public MenuResponse menu(UUID restaurantId) {
        Merchant restaurant = merchants.findById(restaurantId)
                .filter(m -> m.getType() == MerchantType.RESTAURANT
                        && m.getApprovalStatus() == ApprovalStatus.APPROVED
                        && m.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Restaurant"));
        Map<String, List<MenuItem>> byCategory = items.findByMerchantIdOrderByCategoryAscNameAsc(restaurantId).stream()
                .collect(Collectors.groupingBy(CatalogItem::getCategory, LinkedHashMap::new,
                        Collectors.mapping(CatalogService::toMenuItem, Collectors.toList())));
        List<MenuCategory> categories = byCategory.entrySet().stream()
                .map(entry -> new MenuCategory(entry.getKey(), entry.getValue()))
                .toList();
        return new MenuResponse(restaurant.getId(), restaurant.getStoreName(), restaurant.isOpen(), categories);
    }

    /**
     * The virtual-warehouse catalogue: products listed by open Kiranas within 2 km, one row per
     * product. Empty when no Kirana is open nearby.
     */
    @Transactional(readOnly = true)
    public PageResponse<GroceryItem> groceries(double lat, double lng, String query, String category, int page, int size) {
        List<UUID> kiranas = openNearby(MerchantType.KIRANA, lat, lng);
        if (kiranas.isEmpty()) {
            return new PageResponse<>(List.of(), Math.max(page, 0), PageResponse.request(page, size).getPageSize(), 0);
        }
        String trimmedCategory = category == null || category.isBlank() ? null : category.trim();
        return PageResponse.of(items.findGroceries(kiranas, containsPattern(query), trimmedCategory,
                PageResponse.request(page, size)), CatalogService::toGroceryItem);
    }

    /** One search box: matching restaurants (name or cuisine), dishes and groceries nearby. */
    @Transactional(readOnly = true)
    public SearchResponse search(double lat, double lng, String query) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("QUERY_REQUIRED", "Type something to search");
        }
        int radius = properties.ops().serviceRadiusMeters();
        List<RestaurantSummary> restaurants = toSummaries(merchants.findNearby(spatialIndex.cellsWithin(lat, lng, radius),
                MerchantType.RESTAURANT.name(), lat, lng, radius, null, containsPattern(query),
                PageResponse.request(0, SEARCH_LIMIT)).getContent(), new GeoPoint(lat, lng));

        List<UUID> openRestaurants = openNearby(MerchantType.RESTAURANT, lat, lng);
        List<MenuItem> dishes = openRestaurants.isEmpty() ? List.of()
                : items.findTop20ByMerchantIdInAndTypeAndAvailableTrueAndNameContainingIgnoreCase(
                        openRestaurants, ItemType.FOOD, query.trim()).stream().map(CatalogService::toMenuItem).toList();

        List<GroceryItem> groceries = groceries(lat, lng, query, null, 0, PageResponse.DEFAULT_SIZE).items();
        return new SearchResponse(restaurants, dishes, groceries);
    }

    // ----------------------------------------------------------------- shared with cart / addresses

    /** Open, approved stores of a type within the service radius of the point. */
    public List<UUID> openNearby(MerchantType type, double lat, double lng) {
        int radius = properties.ops().serviceRadiusMeters();
        return merchants.findOpenIdsNearby(spatialIndex.cellsWithin(lat, lng, radius), type.name(), lat, lng, radius);
    }

    /** Approved stores of a type (open or not) within the service radius. */
    public long countNearby(MerchantType type, double lat, double lng) {
        int radius = properties.ops().serviceRadiusMeters();
        return merchants.countNearby(spatialIndex.cellsWithin(lat, lng, radius), type.name(), lat, lng, radius);
    }

    public static MenuItem toMenuItem(CatalogItem item) {
        return new MenuItem(item.getId(), item.getName(), item.getDescription(), item.getPrice(),
                Boolean.TRUE.equals(item.getVeg()), item.isAvailable(), item.getImageUrl());
    }

    // ----------------------------------------------------------------- helpers

    /** One travel-time matrix call per page (cheap with OSRM's /table, trivial with haversine). */
    private List<RestaurantSummary> toSummaries(List<NearbyMerchant> rows, GeoPoint customer) {
        if (rows.isEmpty()) {
            return List.of();
        }
        long[][] seconds = routingClient.durationMatrix(
                rows.stream().map(row -> new GeoPoint(row.getLat(), row.getLng())).toList(), List.of(customer));
        int prepMinutes = properties.ops().defaultPrepMinutes();
        return IntStream.range(0, rows.size()).mapToObj(i -> {
            NearbyMerchant row = rows.get(i);
            int travelMinutes = (int) Math.ceil(seconds[i][0] / 60.0);
            return new RestaurantSummary(row.getId(), row.getName(), cuisines(row.getCuisines()), rating(row),
                    (int) Math.round(row.getDistanceMeters()), prepMinutes + travelMinutes, row.getOpen(), row.getImageUrl());
        }).toList();
    }

    private static List<String> cuisines(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).toList();
    }

    private static BigDecimal rating(NearbyMerchant row) {
        return row.getRatingCount() == 0 ? null : BigDecimal.valueOf(row.getRatingSum())
                .divide(BigDecimal.valueOf(row.getRatingCount()), 1, RoundingMode.HALF_UP);
    }

    private static GroceryItem toGroceryItem(GroceryRow row) {
        return new GroceryItem(row.getId(), row.getName(), row.getBrand(), row.getPackSize(), row.getMrp(),
                row.getPrice(), row.getCategory(), TemperatureZone.valueOf(row.getZone()), row.getImageUrl());
    }

    /** {@code %text%} for ILIKE with the user's wildcards escaped, or null for "no filter". */
    static String containsPattern(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
