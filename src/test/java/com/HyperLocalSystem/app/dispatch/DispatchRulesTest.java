package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Phase 8 dispatch rules: rider hand-off on reject, cancel frees the rider, broadcast ring expansion and give-up. */
class DispatchRulesTest extends IntegrationTest {

    @Test
    void rejectedOfferGoesToTheNextRiderAndCancelFreesTheRider() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), true);
        Session nearRider = onlineRider(point(100, 0));
        Session farRider = onlineRider(point(800, 0));
        String dish = createItem(restaurant.token(), "Paneer Roll", "120.00", "Rolls", "HOT");
        Session customer = signUpCustomer();
        String orderId = foodOrder(customer, restaurant.id(), dish, point(0, -300));

        String offer = awaitOffer(nearRider);
        call(HttpMethod.POST, "/api/v1/agent/offers/" + read(offer, "$.items[0].offerId") + "/reject", nearRider.token(),
                "{\"reason\":\"Tyre puncture\"}", 204);
        String next = awaitOffer(farRider);
        assertThat((List<String>) read(next, "$.items[0].orderIds")).containsExactly(orderId);
        assertThat((List<?>) read(get("/api/v1/agent/offers", nearRider.token()), "$.items")).isEmpty();

        post("/api/v1/agent/offers/" + read(next, "$.items[0].offerId") + "/accept", farRider.token(), null);
        assertThat((String) read(get("/api/v1/agent/me", farRider.token()), "$.dutyStatus")).isEqualTo("ACTIVE");

        post("/api/v1/customer/orders/" + orderId + "/cancel", customer.token(), "{\"reason\":\"Ordered twice\"}");
        await().atMost(Duration.ofSeconds(10)).until(() ->
                "AVAILABLE".equals(read(get("/api/v1/agent/me", farRider.token()), "$.dutyStatus")));
        call(HttpMethod.GET, "/api/v1/agent/route", farRider.token(), null, 404);
    }

    @Test
    void unclaimedBroadcastWidensToTheNextRing() throws Exception {
        Session nearKirana = signUpMerchant("KIRANA", "Corner Store", point(0, 300), true);
        Session farKirana = signUpMerchant("KIRANA", "Big Bazaar", point(0, 800), true);
        String milk = createItem(nearKirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":5}".formatted(milk));
        call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201);

        // Round 1 (500 m) reaches only the near shop; it stays silent, so round 2 (1 km) reaches the far one.
        await().atMost(Duration.ofSeconds(5)).until(() -> !((List<?>) read(get("/api/v1/merchant/broadcasts", nearKirana.token()), "$.items")).isEmpty());
        assertThat((List<?>) read(get("/api/v1/merchant/broadcasts", farKirana.token()), "$.items")).isEmpty();
        await().atMost(Duration.ofSeconds(15)).until(() -> !((List<?>) read(get("/api/v1/merchant/broadcasts", farKirana.token()), "$.items")).isEmpty());
        String round2 = get("/api/v1/merchant/broadcasts", farKirana.token());
        assertThat((Boolean) read(post("/api/v1/merchant/broadcasts/" + read(round2, "$.items[0].broadcastId") + "/claim",
                farKirana.token(), null), "$.claimed")).isTrue();
    }

    @Test
    void groceryOnlyOrderWithNoKiranaInRangeIsCancelledAndReleased() throws Exception {
        // 1.5 km away: close enough to order from (2 km), too far for both broadcast rounds (500 m, 1 km).
        Session kirana = signUpMerchant("KIRANA", "Far Store", point(1500, 0), true);
        String rice = createItem(kirana.token(), "Basmati Rice", "120.00", "Staples", "AMBIENT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(rice));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        await().atMost(Duration.ofSeconds(10)).until(() ->
                "CANCELLED".equals(read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")));
        assertThat((String) read(get("/api/v1/payments/orders/" + orderId, customer.token()), "$.status")).isEqualTo("RELEASED");
    }

    private String foodOrder(Session customer, UUID restaurantId, String dish, String location) throws Exception {
        String home = addAddress(customer.token(), location);
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurantId));
        return read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
    }

    private String awaitOffer(Session rider) {
        String[] body = new String[1];
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
            body[0] = get("/api/v1/agent/offers", rider.token());
            return !((List<?>) read(body[0], "$.items")).isEmpty();
        });
        return body[0];
    }
}
