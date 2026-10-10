package com.HyperLocalSystem.app.order;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What the customer app needed: addable dish search results, menu header facts, rated orders, stops on the map. */
class CustomerApiGapsTest extends IntegrationTest {

    @Test
    void searchMenuRatingAndStopsCarryWhatTheAppNeeds() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 1000), true);
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");

        // Dish search: enough to add the dish straight from the results.
        String search = get("/api/v1/catalog/search?" + latLng(0, 0) + "&q=biryani", null);
        assertThat((String) read(search, "$.dishes[0].itemId")).isEqualTo(biryani);
        assertThat((String) read(search, "$.dishes[0].restaurantId")).isEqualTo(restaurant.id().toString());
        assertThat((String) read(search, "$.dishes[0].restaurantName")).isEqualTo("Spice Hub");
        assertThat((Double) read(search, "$.dishes[0].price")).isEqualTo(180.0);
        assertThat((Boolean) read(search, "$.dishes[0].available")).isTrue();

        // Menu header: distance (by road: 1 km x 1.3), ETA, rating, open.
        String menu = get("/api/v1/catalog/restaurants/" + restaurant.id() + "/menu?" + latLng(0, 0), null);
        assertThat((Double) read(menu, "$.distanceKm")).isEqualTo(1.3);
        assertThat((Integer) read(menu, "$.etaMinutes")).isGreaterThan(15);
        assertThat((Object) read(menu, "$.rating")).isNull();
        assertThat((Integer) read(menu, "$.ratingCount")).isZero();
        assertThat((Boolean) read(menu, "$.isOpen")).isTrue();

        // Order: every stop can be put on the map.
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, restaurant.id()));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
        String details = get("/api/v1/customer/orders/" + orderId, customer.token());
        assertThat((List<Object>) read(details, "$.stops[*].lat")).hasSize(2).doesNotContainNull();
        assertThat((List<Object>) read(details, "$.stops[*].lng")).hasSize(2).doesNotContainNull();
        assertThat((String) read(details, "$.stops[0].address")).isEqualTo("1 Market Road");
        assertThat((String) read(details, "$.stops[1].address")).contains("12B");
        assertThat((Boolean) read(details, "$.rated")).isFalse();
        assertThat((Object) read(details, "$.rating")).isNull();

        // Rated: details and the history list say so, the menu counts it.
        jdbc.update("update unified_orders set status = 'COMPLETED' where order_id = ?", UUID.fromString(orderId));
        call(HttpMethod.POST, "/api/v1/customer/orders/" + orderId + "/rating", customer.token(),
                "{\"deliveryRating\":5,\"restaurantRating\":4,\"comment\":\"Hot and fresh\"}", 204);
        details = get("/api/v1/customer/orders/" + orderId, customer.token());
        assertThat((Boolean) read(details, "$.rated")).isTrue();
        assertThat((Integer) read(details, "$.rating.restaurantRating")).isEqualTo(4);
        assertThat((String) read(details, "$.rating.comment")).isEqualTo("Hot and fresh");
        assertThat((Boolean) read(get("/api/v1/customer/orders", customer.token()), "$.items[0].rated")).isTrue();
        menu = get("/api/v1/catalog/restaurants/" + restaurant.id() + "/menu", null);
        assertThat((Double) read(menu, "$.rating")).isEqualTo(4.0);
        assertThat((Integer) read(menu, "$.ratingCount")).isEqualTo(1);
        assertThat((Object) read(menu, "$.distanceKm")).isNull();
    }
}
