package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code app.delivery.fee-distance-basis=ROAD}: cards, menu, cart and checkout all use the road distance
 * (straight line x 1.3 with the default routing), so the free zone ends sooner and slabs come earlier.
 * Visibility stays the straight line.
 */
@TestPropertySource(properties = "app.delivery.fee-distance-basis=ROAD")
class RoadFeeBasisTest extends IntegrationTest {

    @Test
    void feeDistanceAndFreeZoneFollowTheRoad() throws Exception {
        signUpMerchant("RESTAURANT", "Near Dhaba", point(0, 1000), true);
        signUpMerchant("RESTAURANT", "Edge Cafe", point(0, 1700), true);
        Session far = signUpMerchant("RESTAURANT", "Far Biryani", point(0, 4000), true);
        String biryani = createItem(far.token(), "Veg Biryani", "180.00", "Mains", "HOT");

        // 1 km → 1.3 km by road, free; 1.7 km → 2.2 km by road, outside the free zone, ₹15; 4 km → 5.2 km, ₹45.
        String cards = get("/api/v1/catalog/restaurants?" + latLng(0, 0), null);
        assertThat((List<String>) read(cards, "$.items[*].name")).containsExactly("Near Dhaba", "Edge Cafe", "Far Biryani");
        assertThat((List<Double>) read(cards, "$.items[*].distanceKm")).containsExactly(1.3, 2.2, 5.2);
        assertThat((List<Double>) read(cards, "$.items[*].deliveryFee")).containsExactly(0.0, 15.0, 45.0);
        assertThat((List<Integer>) read(cards, "$.items[*].distanceMeters")).allMatch(m -> m <= 4001);

        String menu = get("/api/v1/catalog/restaurants/" + far.id() + "/menu?" + latLng(0, 0), null);
        assertThat((Double) read(menu, "$.distanceKm")).isEqualTo(5.2);
        assertThat((Double) read(menu, "$.deliveryFee")).isEqualTo(45.0);

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, far.id()));
        String cart = put("/api/v1/customer/cart/address", customer.token(), "{\"addressId\":\"%s\"}".formatted(home));
        assertThat((Double) read(cart, "$.deliveryDistanceKm")).isEqualTo(5.2);
        assertThat((Double) read(cart, "$.bill.deliveryFee")).isEqualTo(45.0);
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
        assertThat((Double) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.bill.deliveryFee")).isEqualTo(45.0);
    }
}
