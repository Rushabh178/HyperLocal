package com.HyperLocalSystem.app.notification;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** §3: every WebSocket message is {type, sentAt, data}; rider and customer types end to end. */
class RealtimeEnvelopeTest extends IntegrationTest {

    @Test
    void riderAndCustomerGetEnvelopedMessages() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, 0), true);
        Session rider = onlineRider(point(100, 0));
        String dish = createItem(restaurant.token(), "Paneer Roll", "120.00", "Rolls", "HOT");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, -300));

        // Offer, then the customer cancels: the offer is withdrawn.
        String first = order(customer, restaurant.id(), dish, home);
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", first);
        assertThat(Instant.parse(read(offer, "$.sentAt"))).isBefore(Instant.now().plusSeconds(1));
        post("/api/v1/customer/orders/" + first + "/cancel", customer.token(), "{\"reason\":\"Wrong address\"}");
        String withdrawn = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER_WITHDRAWN", "$.data.orderId", first);
        assertThat((String) read(withdrawn, "$.data.reason")).isEqualTo("CANCELLED");

        // Second order: accepted, then the customer sees the status and the rider moving.
        String second = order(customer, restaurant.id(), dish, home);
        String secondOffer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", second);
        post("/api/v1/agent/offers/" + read(secondOffer, "$.data.offerId") + "/accept", rider.token(), null);
        String status = messages.waitFor(customer.id(), "/queue/tracking", "ORDER_STATUS", "$.data.status", "PICKING");
        assertThat((String) read(status, "$.data.orderId")).isEqualTo(second);

        call(HttpMethod.POST, "/api/v1/agent/me/location", rider.token(), "{\"location\":%s}".formatted(point(50, 0)), 204);
        String moved = messages.waitFor(customer.id(), "/queue/tracking", "RIDER_LOCATION", "$.data.orderId", second);
        assertThat((Double) read(moved, "$.data.location.lat")).isNotNull();
        assertThat((Integer) read(moved, "$.data.etaMinutes")).isPositive();
    }

    private String order(Session customer, UUID restaurantId, String dish, String addressId) throws Exception {
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurantId));
        return read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(addressId), 201), "$.orderId");
    }
}
