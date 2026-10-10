package com.HyperLocalSystem.app.merchant;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the merchant and partner apps need on top of the basic flow: the customer's note and card facts
 * on kitchen tickets, the "Preparing" tab including delays, item deletion, the customer's phone at the
 * door, and the trip's pay breakdown right after delivery.
 */
class MerchantPartnerGapsTest extends IntegrationTest {

    @Test
    void ticketsTabsDeletionContactAndTripBreakdown() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session rider = onlineRider(point(250, 0));
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String naan = createItem(restaurant.token(), "Butter Naan", "40.00", "Breads", "HOT");

        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, restaurant.id()));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\",\"note\":\"Less spicy please\"}".formatted(home), 201), "$.orderId");
        assertThat((String) read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.note")).isEqualTo("Less spicy please");

        // The kitchen ticket: note, age, short code, line ids and zones.
        String ticket = messages.waitFor(restaurant.id(), "/queue/merchant-events", "NEW_ORDER", "$.data.orderId", orderId);
        assertThat((String) read(ticket, "$.data.customerNote")).isEqualTo("Less spicy please");
        assertThat((String) read(ticket, "$.data.createdAt")).isNotBlank();
        assertThat((String) read(ticket, "$.data.shortCode")).isEqualTo(orderId.replace("-", "").substring(28).toUpperCase());
        assertThat((String) read(ticket, "$.data.items[0].itemId")).isNotBlank();
        assertThat((String) read(ticket, "$.data.items[0].zone")).isEqualTo("HOT");

        // A delayed ticket stays on the "Preparing" tab; a list of statuses works too.
        post("/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(), "{\"prepMinutes\":10}");
        post("/api/v1/merchant/orders/" + orderId + "/delay", restaurant.token(), "{\"additionalMinutes\":5}");
        assertThat((List<String>) read(get("/api/v1/merchant/orders?status=PREPARING", restaurant.token()), "$.items[*].orderId"))
                .containsExactly(orderId);
        assertThat((List<String>) read(get("/api/v1/merchant/orders?status=ACCEPTED,DELAYED", restaurant.token()), "$.items[*].status"))
                .containsExactly("DELAYED");

        // Item CRUD: delete.
        call(HttpMethod.DELETE, "/api/v1/merchant/items/" + naan, restaurant.token(), null, 204);
        assertThat((List<String>) read(get("/api/v1/merchant/items", restaurant.token()), "$.items[*].name")).containsExactly("Veg Biryani");

        // Partner: the customer stop has a number to call.
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        String route = post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        assertThat((String) read(route, "$.stops[1].contactPhone")).isEqualTo("+91" + customer.phone());
        assertThat((Object) read(route, "$.stops[0].contactPhone")).isNull();

        String stop = read(route, "$.stops[0].stopId");
        post("/api/v1/merchant/orders/" + orderId + "/ready", restaurant.token(), null);
        post("/api/v1/agent/route/stops/" + stop + "/arrived", rider.token(), null);
        String qr = read(get("/api/v1/merchant/store-qr", restaurant.token()), "$.qrPayload");
        post("/api/v1/agent/route/stops/" + stop + "/scan-qr", rider.token(), "{\"qrPayload\":\"%s\"}".formatted(qr));
        String otp = read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.deliveryOtp");

        // The last delivery returns the (empty) route plus what this trip paid.
        String delivered = post("/api/v1/agent/orders/" + orderId + "/deliver", rider.token(), "{\"deliveryOtp\":\"%s\"}".formatted(otp));
        assertThat((List<?>) read(delivered, "$.stops")).isEmpty();
        assertThat((String) read(delivered, "$.completedTrip.orderId")).isEqualTo(orderId);
        assertThat((Double) read(delivered, "$.completedTrip.breakdown.basePay")).isEqualTo(30.0);
        assertThat((Double) read(delivered, "$.completedTrip.breakdown.distancePay")).isEqualTo(0.0);
        assertThat((Double) read(delivered, "$.completedTrip.breakdown.total")).isEqualTo(30.0);
        assertThat((Double) read(get("/api/v1/agent/deliveries/" + orderId, rider.token()), "$.breakdown.total")).isEqualTo(30.0);
        assertThat((Double) read(get("/api/v1/agent/deliveries", rider.token()), "$.items[0].breakdown.basePay")).isEqualTo(30.0);
    }
}
