package com.HyperLocalSystem.app.delivery;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Delivery range and pricing: restaurants visible to 6 km with a distance fee (road km = straight line x 1.3
 * with the default routing), Kiranas only within 2 km of the customer, the meal nudge only within 2 km, and
 * the rider paid per km beyond 2 km.
 */
class DeliveryRangeTest extends IntegrationTest {

    @Test
    void restaurantAt4KmIsVisibleWithAFeeAndTheRiderIsPaidForTheDistance() throws Exception {
        signUpMerchant("RESTAURANT", "Near Dhaba", point(0, 1000), true);
        Session far = signUpMerchant("RESTAURANT", "Far Biryani", point(0, 4000), true);
        Session tooFar = signUpMerchant("RESTAURANT", "Other Town", point(0, -7000), true);
        Session rider = onlineRider(point(0, 4050));
        String biryani = createItem(far.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String thali = createItem(tooFar.token(), "Thali", "150.00", "Mains", "HOT");

        // Cards: 1 km → free; 4 km (5.2 km by road) → the 5-6 km slab, ₹45; 7 km → not listed.
        String cards = get("/api/v1/catalog/restaurants?" + latLng(0, 0), null);
        assertThat((List<String>) read(cards, "$.items[*].name")).containsExactly("Near Dhaba", "Far Biryani");
        assertThat((Double) read(cards, "$.items[0].deliveryFee")).isEqualTo(0.0);
        assertThat((Double) read(cards, "$.items[1].distanceKm")).isEqualTo(5.2);
        assertThat((Double) read(cards, "$.items[1].deliveryFee")).isEqualTo(45.0);
        String menu = get("/api/v1/catalog/restaurants/" + far.id() + "/menu?" + latLng(0, 0), null);
        assertThat((Double) read(menu, "$.deliveryFee")).isEqualTo(45.0);
        assertThat((Double) read(menu, "$.distanceKm")).isEqualTo(5.2);

        // Address check names the part that can't come.
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(thali, tooFar.id()));
        String refused = call(HttpMethod.PUT, "/api/v1/customer/cart/address", customer.token(), "{\"addressId\":\"%s\"}".formatted(home), 400);
        assertThat((String) read(refused, "$.message")).startsWith("Food from Other Town can't be delivered").contains("6 km");

        // Cart and checkout charge the slab; the bill stays food-only free of the meal waiver.
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, far.id()));
        String cart = put("/api/v1/customer/cart/address", customer.token(), "{\"addressId\":\"%s\"}".formatted(home));
        assertThat((Double) read(cart, "$.bill.deliveryFee")).isEqualTo(45.0);
        assertThat((Double) read(cart, "$.deliveryDistanceKm")).isEqualTo(5.2);
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
        assertThat((Double) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.bill.deliveryFee")).isEqualTo(45.0);

        // Rider: base ₹30 + ₹5/km for the 3.2 km beyond 2 km.
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        assertThat((Double) read(offer, "$.data.estimatedEarnings")).isCloseTo(46.0, within(0.05));
        String route = post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        String stop = read(route, "$.stops[0].stopId");
        post("/api/v1/merchant/orders/" + orderId + "/accept", far.token(), "{\"prepMinutes\":1}");
        post("/api/v1/merchant/orders/" + orderId + "/ready", far.token(), null);
        post("/api/v1/agent/route/stops/" + stop + "/arrived", rider.token(), null);
        String qr = read(get("/api/v1/merchant/store-qr", far.token()), "$.qrPayload");
        post("/api/v1/agent/route/stops/" + stop + "/scan-qr", rider.token(), "{\"qrPayload\":\"%s\"}".formatted(qr));
        String otp = read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.deliveryOtp");
        post("/api/v1/agent/orders/" + orderId + "/deliver", rider.token(), "{\"deliveryOtp\":\"%s\"}".formatted(otp));
        String earnings = get("/api/v1/agent/earnings?period=TODAY", rider.token());
        assertThat((Double) read(earnings, "$.distancePay")).isCloseTo(16.0, within(0.05));
        assertThat((Double) read(earnings, "$.total")).isCloseTo(46.0, within(0.05));
        assertThat(jdbc.queryForObject("select count(*) from agent_earnings where order_id = ? and earning_type = 'DISTANCE'",
                Integer.class, UUID.fromString(orderId))).isEqualTo(1);
    }

    @Test
    void kiranaAt3KmIsNotUsed() throws Exception {
        // The restaurant is 2.8 km away, so the broadcast starts at the customer instead of next to it.
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 2800), true);
        Session farKirana = signUpMerchant("KIRANA", "Far Kirana", point(0, 3000), true);
        Session nearKirana = signUpMerchant("KIRANA", "Near Kirana", point(0, -800), true);
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(nearKirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        createItem(farKirana.token(), "Toned Milk", "28.00", "Dairy", "COLD");
        createItem(farKirana.token(), "Far Paneer", "90.00", "Dairy", "COLD");

        // Only the near Kirana's shelf is in the catalogue.
        String groceries = get("/api/v1/catalog/groceries?" + latLng(0, 0), null);
        assertThat((List<String>) read(groceries, "$.items[*].name")).containsExactly("Toned Milk");
        assertThat((Double) read(groceries, "$.items[0].price")).isEqualTo(30.0);

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, restaurant.id()));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(milk));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Round 2 (1 km around the customer) reaches the near shop; the shop 3 km away is never asked.
        messages.waitFor(nearKirana.id(), "/queue/merchant-events", "BROADCAST_OFFER", "$.data.orderId", orderId);
        assertThat((List<?>) read(get("/api/v1/merchant/broadcasts", farKirana.token()), "$.items")).isEmpty();
        assertThat(messages.all(farKirana.id(), "/queue/merchant-events")).noneMatch(m -> m.contains("BROADCAST_OFFER"));
    }

    @Test
    void mealNudgeExcludesA3KmRestaurant() throws Exception {
        Session near = signUpMerchant("RESTAURANT", "Near Dhaba", point(1000, 0), true);
        Session far = signUpMerchant("RESTAURANT", "Far Biryani", point(-3000, 0), true);
        Session kirana = signUpMerchant("KIRANA", "Corner Store", point(0, 300), true);
        createItem(near.token(), "Dal Rice", "120.00", "Mains", "HOT");
        createItem(far.token(), "Mutton Biryani", "400.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(milk));
        String cart = put("/api/v1/customer/cart/address", customer.token(), "{\"addressId\":\"%s\"}".formatted(home));

        // Grocery-only under ₹150 inside the free zone: ₹25, and only the 1 km restaurant is suggested.
        assertThat((Double) read(cart, "$.bill.deliveryFee")).isEqualTo(25.0);
        assertThat((List<String>) read(cart, "$.nudge.suggestedMeals[*].restaurantName")).containsExactly("Near Dhaba");
        assertThat((List<String>) read(get("/api/v1/customer/cart/nudges", customer.token()), "$.suggestedMeals[*].name"))
                .containsExactly("Dal Rice");
    }
}
