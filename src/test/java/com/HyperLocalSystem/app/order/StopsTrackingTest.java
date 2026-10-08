package com.HyperLocalSystem.app.order;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** §4: stops in order details / tracking, and STOPS_UPDATED with missing items and the bill. */
class StopsTrackingTest extends IntegrationTest {

    @Test
    void customerSeesStopsAndMissingItemsLive() throws Exception {
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(0, 200), true);
        Session rider = onlineRider(point(0, 150));
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        String bread = createItem(kirana.token(), "Brown Bread", "45.00", "Bakery", "AMBIENT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":4}".formatted(milk));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(bread));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // Before a rider: planned stops without ids.
        String planned = get("/api/v1/customer/orders/" + orderId, customer.token());
        assertThat((List<String>) read(planned, "$.stops[*].type")).containsExactly("KIRANA", "CUSTOMER");
        assertThat((Object) read(planned, "$.stops[0].stopId")).isNull();

        String broadcast = messages.waitFor(kirana.id(), "/queue/merchant-events", "BROADCAST_OFFER", "$.data.orderId", orderId);
        post("/api/v1/merchant/broadcasts/" + read(broadcast, "$.data.broadcastId") + "/claim", kirana.token(), null);
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        String route = post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        String kiranaStop = read(route, "$.stops[0].stopId");

        // The rider arrives: STOPS_UPDATED shows ARRIVED; tracking has the same real stops.
        post("/api/v1/agent/route/stops/" + kiranaStop + "/arrived", rider.token(), null);
        String arrived = messages.waitFor(customer.id(), "/queue/tracking", "STOPS_UPDATED", "$.data.stops[0].status", "ARRIVED");
        assertThat((String) read(arrived, "$.data.stops[0].stopId")).isEqualTo(kiranaStop);
        String tracking = get("/api/v1/customer/orders/" + orderId + "/tracking", customer.token());
        assertThat((List<String>) read(tracking, "$.stops[*].status")).containsExactly("ARRIVED", "PENDING");

        // Bread is missing: STOPS_UPDATED carries it with its refund and the bill after refunds.
        String breadLine = read(get("/api/v1/agent/route", rider.token()), "$.stops[0].items[?(@.name == 'Brown Bread')].itemId")
                .toString().replaceAll("[\\[\\]\"]", "");
        post("/api/v1/agent/route/stops/" + kiranaStop + "/items/" + breadLine + "/deficient", rider.token(), null);
        String missing = messages.waitFor(customer.id(), "/queue/tracking", "STOPS_UPDATED", "$.data.missingItems[0].name", "Brown Bread");
        assertThat((Double) read(missing, "$.data.missingItems[0].refundAmount")).isEqualTo(45.0);
        // 120 milk + 45 bread + 10 convenience fee (no delivery fee above ₹150) = 175; bread goes back.
        assertThat((Double) read(missing, "$.data.bill.total")).isEqualTo(175.0);
        assertThat((Double) read(missing, "$.data.bill.refunds")).isEqualTo(45.0);
        assertThat((Double) read(missing, "$.data.bill.netTotal")).isEqualTo(130.0);
    }
}
