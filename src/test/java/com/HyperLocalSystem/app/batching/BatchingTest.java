package com.HyperLocalSystem.app.batching;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Phase 10: a rider is carrying a food order north along a straight road; a grocery-only order
 * whose Kirana and customer sit just off that road is slipped into the route instead of getting
 * its own rider.
 */
class BatchingTest extends IntegrationTest {

    @Test
    void groceryOrderAlongTheWayIsInjectedIntoTheActiveRoute() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), true);
        Session kirana = signUpMerchant("KIRANA", "Wayside Kirana", point(500, 80), true);
        Session rider = onlineRider(point(-50, 0));
        String dish = createItem(restaurant.token(), "Masala Dosa", "90.00", "Dosa", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");

        // First customer, 1.5 km up the road: the rider takes it and heads Restaurant → Customer 1.
        Session first = signUpCustomer();
        String firstHome = addAddress(first.token(), point(1500, 0));
        post("/api/v1/customer/cart/items", first.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurant.id()));
        checkout(first, firstHome);
        await().atMost(Duration.ofSeconds(15)).until(() -> !((List<?>) read(get("/api/v1/agent/offers", rider.token()), "$.items")).isEmpty());
        String offerId = read(get("/api/v1/agent/offers", rider.token()), "$.items[0].offerId");
        post("/api/v1/agent/offers/" + offerId + "/accept", rider.token(), null);

        // Second customer, just off the same road, orders only groceries; the Kirana claims them.
        Session second = signUpCustomer();
        String secondHome = addAddress(second.token(), point(1200, 60));
        post("/api/v1/customer/cart/items", second.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(milk));
        String secondOrder = checkout(second, secondHome);
        await().atMost(Duration.ofSeconds(15)).until(() -> !((List<?>) read(get("/api/v1/merchant/broadcasts", kirana.token()), "$.items")).isEmpty());
        String broadcastId = read(get("/api/v1/merchant/broadcasts", kirana.token()), "$.items[0].broadcastId");
        post("/api/v1/merchant/broadcasts/" + broadcastId + "/claim", kirana.token(), null);

        // Injected, not offered: the rider's route becomes Restaurant → Kirana → Customer 2 → Customer 1.
        await().atMost(Duration.ofSeconds(15)).until(() ->
                "BATCHED".equals(read(get("/api/v1/customer/orders/" + secondOrder, second.token()), "$.status")));
        String route = get("/api/v1/agent/route", rider.token());
        assertThat((List<String>) read(route, "$.stops[*].type")).containsExactly("RESTAURANT", "KIRANA", "CUSTOMER", "CUSTOMER");
        assertThat((List<String>) read(route, "$.stops[2].orderIds")).containsExactly(secondOrder);
        assertThat((List<?>) read(get("/api/v1/agent/offers", rider.token()), "$.items")).isEmpty();

        String earnings = get("/api/v1/agent/earnings?period=TODAY", rider.token());
        assertThat((Integer) read(earnings, "$.batchedInjections")).isEqualTo(1);
        assertThat((Double) read(earnings, "$.incentives")).isEqualTo(15.0);

        UUID injectionId = jdbc.queryForObject("select injection_id from route_injections where injected_order_id = ?",
                UUID.class, UUID.fromString(secondOrder));
        call(HttpMethod.POST, "/api/v1/agent/route/injections/" + injectionId + "/ack", rider.token(), null, 204);
        assertThat(jdbc.queryForObject("select added_delay_seconds from route_injections where injection_id = ?", Long.class, injectionId))
                .isLessThanOrEqualTo(240L);
    }

    private String checkout(Session customer, String addressId) throws Exception {
        return read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(addressId), 201), "$.orderId");
    }
}
