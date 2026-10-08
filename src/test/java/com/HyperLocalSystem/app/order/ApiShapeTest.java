package com.HyperLocalSystem.app.order;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §7: money format, list shape, grocery categories, nudge ids, order details and earnings. */
class ApiShapeTest extends IntegrationTest {

    @Test
    void responsesHaveTheShapeTheAppsExpect() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 300), true);
        Session kirana1 = signUpMerchant("KIRANA", "Sharma Kirana", point(0, 200), true);
        Session kirana2 = signUpMerchant("KIRANA", "Gupta Stores", point(0, -200), true);
        Session rider = onlineRider(point(0, 250));
        String dish = createItem(restaurant.token(), "Veg Thali", "150.00", "Thalis", "HOT");
        String milk = createItem(kirana1.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        createItem(kirana1.token(), "Brown Bread", "45.00", "Bakery", "AMBIENT");
        createItem(kirana2.token(), "Toned Milk", "28.00", "Dairy", "COLD");
        createItem(kirana2.token(), "Curd", "40.00", "Dairy", "COLD");

        // Grocery categories with distinct product counts (the two milks are one product).
        String categories = get("/api/v1/catalog/groceries/categories?" + latLng(0, 0), null);
        assertThat((List<String>) read(categories, "$.items[*].category")).containsExactly("Bakery", "Dairy");
        assertThat((List<Integer>) read(categories, "$.items[*].itemCount")).containsExactly(1, 2);

        // Money: always two decimals; the nudge suggests dishes with their ids.
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        String cart = post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":1}".formatted(milk));
        assertThat(cart).contains("\"deliveryFee\":25.00", "\"convenienceFee\":10.00", "\"total\":65.00");
        assertThat((String) read(cart, "$.nudge.suggestedMeals[0].itemId")).isEqualTo(dish);
        assertThat((String) read(cart, "$.nudge.suggestedMeals[0].restaurantId")).isEqualTo(restaurant.id().toString());

        // Every list: {items, page, size, totalItems}, 0-based.
        String addresses = get("/api/v1/customer/addresses", customer.token());
        assertThat((Integer) read(addresses, "$.page")).isZero();
        assertThat((Integer) read(addresses, "$.totalItems")).isEqualTo(1);

        // Order details: cancellable, missing items, rider with name / phone / vehicle.
        call(HttpMethod.DELETE, "/api/v1/customer/cart", customer.token(), null, 204);
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurant.id()));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        assertThat(offer).contains("\"estimatedEarnings\":30.00");
        post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        String detail = get("/api/v1/customer/orders/" + orderId, customer.token());
        assertThat((Boolean) read(detail, "$.cancellable")).isTrue();
        assertThat((List<?>) read(detail, "$.missingItems")).isEmpty();
        assertThat((String) read(detail, "$.rider.name")).isEqualTo("Test Rider");
        assertThat((String) read(detail, "$.rider.phone")).isEqualTo("+91" + rider.phone());
        assertThat(detail).contains("\"netTotal\":167.50");

        // Earnings, one style: totals, a count and paged payouts; partners also get today's online minutes.
        jdbc.update("insert into agent_duty_sessions (session_id, agent_id, started_at, ended_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), rider.id(), Timestamp.from(Instant.now().minusSeconds(180)), Timestamp.from(Instant.now().minusSeconds(60)));
        String partner = get("/api/v1/agent/earnings?period=TODAY", rider.token());
        assertThat((Integer) read(partner, "$.tripCount")).isZero();
        assertThat((Integer) read(partner, "$.onlineMinutes")).isGreaterThanOrEqualTo(2);
        assertThat((Integer) read(partner, "$.payouts.page")).isZero();
        assertThat(partner).contains("\"total\":0.00");
        String store = get("/api/v1/merchant/earnings?period=WEEK", restaurant.token());
        assertThat((Integer) read(store, "$.orderCount")).isZero();
        assertThat((List<?>) read(store, "$.payouts.items")).isEmpty();
        assertThat(store).contains("\"total\":0.00");
    }
}
