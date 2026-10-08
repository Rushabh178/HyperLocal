package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** §1: the merchant app's live events for one combined order, plus a cancellation. */
class MerchantEventsTest extends IntegrationTest {

    private static final String QUEUE = "/queue/merchant-events";

    @Test
    void merchantsFollowTheOrderLive() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session fastKirana = signUpMerchant("KIRANA", "Sharma Kirana", point(300, 200), true);
        Session slowKirana = signUpMerchant("KIRANA", "Gupta Stores", point(300, -200), true);
        Session rider = onlineRider(point(250, 0));
        String dish = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(fastKirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurant.id()));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(milk));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Restaurant: NEW_ORDER once confirmed. Kiranas: BROADCAST_OFFER with a deadline.
        String newOrder = messages.waitFor(restaurant.id(), QUEUE, "NEW_ORDER", "$.data.orderId", orderId);
        assertThat((String) read(newOrder, "$.data.status")).isEqualTo("NEW");
        String offer = messages.waitFor(slowKirana.id(), QUEUE, "BROADCAST_OFFER", "$.data.orderId", orderId);
        assertThat(Instant.parse(read(offer, "$.data.expiresAt"))).isAfter(Instant.now());

        // One Kirana claims; the other gets BROADCAST_TAKEN.
        post("/api/v1/merchant/broadcasts/" + read(offer, "$.data.broadcastId") + "/claim", fastKirana.token(), null);
        String taken = messages.waitFor(slowKirana.id(), QUEUE, "BROADCAST_TAKEN", "$.data.orderId", orderId);
        assertThat((String) read(taken, "$.data.reason")).isEqualTo("CLAIMED");

        // Rider at the Kirana: RIDER_ARRIVING, then ORDER_PICKED_UP after the QR scan.
        String riderOffer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        String route = post("/api/v1/agent/offers/" + read(riderOffer, "$.data.offerId") + "/accept", rider.token(), null);
        String kiranaStop = read(route, "$.stops[0].stopId");
        post("/api/v1/agent/route/stops/" + kiranaStop + "/arrived", rider.token(), null);
        String arriving = messages.waitFor(fastKirana.id(), QUEUE, "RIDER_ARRIVING", "$.data.orderId", orderId);
        assertThat((String) read(arriving, "$.data.riderName")).isEqualTo("Test Rider");
        String qr = read(get("/api/v1/merchant/store-qr", fastKirana.token()), "$.qrPayload");
        post("/api/v1/agent/route/stops/" + kiranaStop + "/kirana-qr", rider.token(), "{\"qrPayload\":\"%s\"}".formatted(qr));
        messages.waitFor(fastKirana.id(), QUEUE, "ORDER_PICKED_UP", "$.data.stopId", kiranaStop);

        // A second order is cancelled: the restaurant hears about it.
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurant.id()));
        String second = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
        messages.waitFor(restaurant.id(), QUEUE, "NEW_ORDER", "$.data.orderId", second);
        post("/api/v1/customer/orders/" + second + "/cancel", customer.token(), "{\"reason\":\"Changed my mind\"}");
        String cancelled = messages.waitFor(restaurant.id(), QUEUE, "ORDER_CANCELLED", "$.data.orderId", second);
        assertThat((String) read(cancelled, "$.data.reason")).isEqualTo("Changed my mind");

        // Exactly one NEW_ORDER per order.
        List<String> all = messages.all(restaurant.id(), QUEUE);
        assertThat(all.stream().filter(m -> m.contains("\"NEW_ORDER\"") && m.contains(orderId)).count()).isEqualTo(1);
    }
}
