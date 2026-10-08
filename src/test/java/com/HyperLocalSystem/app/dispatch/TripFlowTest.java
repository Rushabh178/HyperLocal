package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Phase 8, the main flow end to end: a combined order is broadcast and claimed, offered to the
 * nearest rider, picked up Kirana → Restaurant (one item missing) and delivered with the OTP.
 */
class TripFlowTest extends IntegrationTest {

    @Test
    void combinedOrderFromCheckoutToDoorstep() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(300, 200), true);
        Session rider = onlineRider(point(250, 0));
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        String bread = createItem(kirana.token(), "Brown Bread", "45.00", "Bakery", "AMBIENT");

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        addToCart(customer, "FOOD", biryani, restaurant.id(), 1);
        addToCart(customer, "GROCERY", milk, null, 2);
        addToCart(customer, "GROCERY", bread, null, 1);
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Parallel dispatch: the Kirana gets the broadcast and the nearest rider gets the offer.
        String broadcast = awaitNonEmpty("/api/v1/merchant/broadcasts", kirana.token(), "$");
        assertThat((Boolean) read(post("/api/v1/merchant/broadcasts/" + read(broadcast, "$[0].broadcastId") + "/claim",
                kirana.token(), null), "$.claimed")).isTrue();
        String offers = awaitNonEmpty("/api/v1/agent/offers", rider.token(), "$");
        assertThat((Double) read(offers, "$[0].estimatedEarnings")).isEqualTo(40.0);

        String route = post("/api/v1/agent/offers/" + read(offers, "$[0].offerId") + "/accept", rider.token(), null);
        assertThat((List<String>) read(route, "$.stops[*].type")).containsExactly("KIRANA", "RESTAURANT", "CUSTOMER");
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")).isEqualTo("PICKING");
        String kiranaStop = read(route, "$.stops[0].stopId");
        String restaurantStop = read(route, "$.stops[1].stopId");

        post("/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(), "{\"prepMinutes\":1}");
        post("/api/v1/merchant/orders/" + orderId + "/ready", restaurant.token(), null);

        // Linear route locking: the restaurant can't be done before the Kirana.
        String locked = call(HttpMethod.POST, "/api/v1/agent/route/stops/" + restaurantStop + "/picked-up", rider.token(), null, 409);
        assertThat((String) read(locked, "$.code")).isEqualTo("STOP_LOCKED");

        // At the Kirana: bread is missing (refunded to the wallet), then the shop's QR releases its payment.
        post("/api/v1/agent/route/stops/" + kiranaStop + "/arrived", rider.token(), null);
        String breadLine = read(get("/api/v1/agent/route", rider.token()), "$.stops[0].items[?(@.name == 'Brown Bread')].itemId")
                .toString().replaceAll("[\\[\\]\"]", "");
        post("/api/v1/agent/route/stops/" + kiranaStop + "/items/" + breadLine + "/deficient", rider.token(), "{\"reason\":\"Out of stock\"}");
        String qr = read(get("/api/v1/merchant/store-qr", kirana.token()), "$.qrPayload");
        route = post("/api/v1/agent/route/stops/" + kiranaStop + "/kirana-qr", rider.token(), "{\"qrPayload\":\"%s\"}".formatted(qr));
        assertThat((String) read(route, "$.stops[0].state")).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select amount from merchant_settlements where merchant_id = ? and order_id = ?",
                Double.class, kirana.id(), UUID.fromString(orderId))).isEqualTo(60.0);

        post("/api/v1/agent/route/stops/" + restaurantStop + "/arrived", rider.token(), null);
        post("/api/v1/agent/route/stops/" + restaurantStop + "/picked-up", rider.token(), null);
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")).isEqualTo("OUT_FOR_DELIVERY");

        // At the door: the wrong code is refused, the right one completes the trip and frees the rider.
        String otp = read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.deliveryOtp");
        String wrongOtp = otp.equals("0000") ? "1111" : "0000";
        call(HttpMethod.POST, "/api/v1/agent/orders/" + orderId + "/deliver", rider.token(), "{\"deliveryOtp\":\"%s\"}".formatted(wrongOtp), 400);
        route = post("/api/v1/agent/orders/" + orderId + "/deliver", rider.token(), "{\"deliveryOtp\":\"%s\"}".formatted(otp));
        assertThat((List<?>) read(route, "$.stops")).isEmpty();
        assertThat((String) read(get("/api/v1/agent/me", rider.token()), "$.dutyStatus")).isEqualTo("AVAILABLE");

        // Rider: base ₹30 + ₹10 for the second pickup.
        String earnings = get("/api/v1/agent/earnings?period=TODAY", rider.token());
        assertThat((Integer) read(earnings, "$.deliveries")).isEqualTo(1);
        assertThat((Double) read(earnings, "$.total")).isEqualTo(40.0);
        assertThat((Integer) read(get("/api/v1/agent/deliveries", rider.token()), "$.items[0].stops")).isEqualTo(3);

        // Customer: completed timeline, ₹45 back in the wallet, payment partly refunded, rating counted.
        String timeline = get("/api/v1/customer/orders/" + orderId + "/timeline", customer.token());
        assertThat((List<String>) read(timeline, "$.stops[*].state")).containsOnly("COMPLETED");
        assertThat((Double) read(get("/api/v1/customer/wallet", customer.token()), "$.balance")).isEqualTo(45.0);
        assertThat((String) read(get("/api/v1/payments/orders/" + orderId, customer.token()), "$.status")).isEqualTo("PARTIALLY_REFUNDED");
        call(HttpMethod.POST, "/api/v1/customer/orders/" + orderId + "/rating", customer.token(),
                "{\"deliveryRating\":5,\"restaurantRating\":4,\"kiranaRating\":5}", 204);
        assertThat((Double) read(get("/api/v1/catalog/restaurants?" + latLng(0, 0), null), "$.items[0].rating")).isEqualTo(4.0);
    }

    // ----------------------------------------------------------------- helpers

    private void addToCart(Session customer, String type, String itemId, UUID restaurantId, int quantity) throws Exception {
        String restaurant = restaurantId == null ? "" : ",\"restaurantId\":\"%s\"".formatted(restaurantId);
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"%s\",\"itemId\":\"%s\",\"quantity\":%d%s}".formatted(type, itemId, quantity, restaurant));
    }

    private String awaitNonEmpty(String url, String token, String path) {
        String[] body = new String[1];
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            body[0] = get(url, token);
            return !((List<?>) read(body[0], path)).isEmpty();
        });
        return body[0];
    }
}
