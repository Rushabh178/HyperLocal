package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §5: Arrived → missing items (Kirana) → scan QR, at the Kirana and the restaurant. */
class PickupSequenceTest extends IntegrationTest {

    @Test
    void everyPickupIsArrivedThenScan() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 300), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(200, 300), true);
        Session rider = onlineRider(point(0, 250));
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        String bread = createItem(kirana.token(), "Brown Bread", "45.00", "Bakery", "AMBIENT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, restaurant.id()));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(milk));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(bread));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Grocery part: BROADCASTING → CLAIMED (shown as ACCEPTED) → READY through the shared /ready endpoint.
        String broadcast = messages.waitFor(kirana.id(), "/queue/merchant-events", "BROADCAST_OFFER", "$.data.orderId", orderId);
        post("/api/v1/merchant/broadcasts/" + read(broadcast, "$.data.broadcastId") + "/claim", kirana.token(), null);
        assertThat((String) read(get("/api/v1/merchant/orders", kirana.token()), "$.items[0].status")).isEqualTo("ACCEPTED");
        assertThat((String) read(post("/api/v1/merchant/orders/" + orderId + "/ready", kirana.token(), null), "$.status")).isEqualTo("READY");

        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        String route = post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        String kiranaStop = read(route, "$.stops[0].stopId");
        String restaurantStop = read(route, "$.stops[1].stopId");
        String breadLine = read(route, "$.stops[0].items[1].itemId");
        String kiranaQr = qr(kirana);
        String restaurantQr = qr(restaurant);

        // Kirana: no scan before Arrived; missing items only between Arrived and the scan.
        String early = call(HttpMethod.POST, "/api/v1/agent/route/stops/" + kiranaStop + "/scan-qr", rider.token(), kiranaQr, 409);
        assertThat((String) read(early, "$.code")).isEqualTo("NOT_ARRIVED");
        post("/api/v1/agent/route/stops/" + kiranaStop + "/arrived", rider.token(), null);
        post("/api/v1/agent/route/stops/" + kiranaStop + "/deficient", rider.token(), "{\"itemIds\":[\"%s\"]}".formatted(breadLine));
        post("/api/v1/agent/route/stops/" + kiranaStop + "/scan-qr", rider.token(), kiranaQr);
        String late = call(HttpMethod.POST, "/api/v1/agent/route/stops/" + kiranaStop + "/deficient", rider.token(),
                "{\"itemIds\":[\"%s\"]}".formatted(breadLine), 409);
        assertThat((String) read(late, "$.code")).isEqualTo("STOP_ALREADY_COMPLETED");

        // The Kirana is settled for what it handed over (2 x 30); the bread (45) went to the wallet.
        assertThat(settlement(kirana.id(), orderId)).isEqualTo(60.0);
        assertThat((Double) read(get("/api/v1/customer/wallet", customer.token()), "$.balance")).isEqualTo(45.0);

        // Restaurant: same sequence, and it only accepts its own QR.
        post("/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(), "{\"prepMinutes\":1}");
        post("/api/v1/merchant/orders/" + orderId + "/ready", restaurant.token(), null);
        post("/api/v1/agent/route/stops/" + restaurantStop + "/arrived", rider.token(), null);
        String wrong = call(HttpMethod.POST, "/api/v1/agent/route/stops/" + restaurantStop + "/scan-qr", rider.token(), kiranaQr, 400);
        assertThat((String) read(wrong, "$.code")).isEqualTo("QR_WRONG_STORE");
        post("/api/v1/agent/route/stops/" + restaurantStop + "/scan-qr", rider.token(), restaurantQr);
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.status")).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(settlement(restaurant.id(), orderId)).isEqualTo(180.0);
    }

    private String qr(Session merchant) throws Exception {
        return "{\"qrPayload\":\"%s\"}".formatted((String) read(get("/api/v1/merchant/store-qr", merchant.token()), "$.qrPayload"));
    }

    private Double settlement(UUID merchantId, String orderId) {
        return jdbc.queryForObject("select amount from merchant_settlements where merchant_id = ? and order_id = ?",
                Double.class, merchantId, UUID.fromString(orderId));
    }
}
