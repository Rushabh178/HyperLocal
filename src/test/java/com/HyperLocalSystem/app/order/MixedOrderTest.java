package com.HyperLocalSystem.app.order;

import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.order.service.UnfulfilledGroceries;
import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Food + groceries in one order: the rider waits for the groceries to be claimed, and groceries that
 * never reach the bag are never paid for (one refund, whichever path gives them up).
 */
class MixedOrderTest extends IntegrationTest {

    @Autowired
    private UnfulfilledGroceries unfulfilledGroceries;

    @Autowired
    private UnifiedOrderRepository orders;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void mixedOrderWaitsForClaimBeforeRiderOffer() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, -300), true);
        Session kirana = signUpMerchant("KIRANA", "Sharma Kirana", point(200, -300), true);
        Session rider = onlineRider(point(0, -250));
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String orderId = mixedOrder(customer, restaurant.id(), biryani, milk);

        // The Kirana is asked, the restaurant has its ticket, but no rider is offered the order yet.
        messages.waitFor(kirana.id(), "/queue/merchant-events", "BROADCAST_OFFER", "$.data.orderId", orderId);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() ->
                ((List<?>) read(get("/api/v1/agent/offers", rider.token()), "$.items")).isEmpty());

        String broadcastId = read(get("/api/v1/merchant/broadcasts", kirana.token()), "$.items[0].broadcastId");
        post("/api/v1/merchant/broadcasts/" + broadcastId + "/claim", kirana.token(), null);

        // Claimed: now the rider gets it, Kirana first.
        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        assertThat((List<String>) read(offer, "$.data.stops[*].type")).containsExactly("KIRANA", "RESTAURANT", "CUSTOMER");
    }

    @Test
    void groceryNeverClaimedFoodDeliveredGroceryRefunded() throws Exception {
        Delivered order = deliverMixedOrderWithoutAnyKiranaInRange();

        // The customer pays for the food only: ₹60 of milk back in the wallet, the bill says so.
        assertThat((Double) read(get("/api/v1/customer/wallet", order.customer().token()), "$.balance")).isEqualTo(60.0);
        String details = get("/api/v1/customer/orders/" + order.orderId(), order.customer().token());
        assertThat((String) read(details, "$.status")).isEqualTo("COMPLETED");
        assertThat((Double) read(details, "$.bill.refunds")).isEqualTo(60.0);
        assertThat(new BigDecimal(read(details, "$.bill.netTotal").toString()))
                .isEqualByComparingTo(new BigDecimal(read(details, "$.bill.total").toString()).subtract(new BigDecimal("60")));
        assertThat((List<String>) read(details, "$.stops[?(@.type == 'KIRANA')].status")).containsExactly("SKIPPED");
        assertThat((String) read(get("/api/v1/payments/orders/" + order.orderId(), order.customer().token()), "$.status"))
                .isEqualTo("PARTIALLY_REFUNDED");

        // Live: the refund message, plus a repaint of the stops with the refund in the bill.
        String unfulfilled = messages.waitFor(order.customer().id(), "/queue/tracking", "GROCERIES_UNFULFILLED", "$.data.orderId", order.orderId());
        assertThat((Double) read(unfulfilled, "$.data.refundAmount")).isEqualTo(60.0);
        messages.waitFor(order.customer().id(), "/queue/tracking", "STOPS_UPDATED", "$.data.bill.refunds", 60.0);
    }

    @Test
    void refundRunsOnceEvenIfBothPathsFire() throws Exception {
        // The broadcast gave up (refund #1 path) and the drop completed without groceries (refund #2 path).
        Delivered order = deliverMixedOrderWithoutAnyKiranaInRange();
        UUID orderId = UUID.fromString(order.orderId());
        // ...and once more, as if a third trigger fired late.
        BigDecimal again = new TransactionTemplate(transactionManager).execute(status ->
                unfulfilledGroceries.apply(orders.findById(orderId).orElseThrow(), "late duplicate"));

        assertThat(again).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select count(*) from wallet_entries where order_id = ? and type = 'CREDIT'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select refunded_amount from payments where order_id = ?", BigDecimal.class, orderId))
                .isEqualByComparingTo("60.00");
        assertThat((Double) read(get("/api/v1/customer/wallet", order.customer().token()), "$.balance")).isEqualTo(60.0);
    }

    // ----------------------------------------------------------------- helpers

    private record Delivered(Session customer, String orderId) {
    }

    /**
     * Biryani + 2 milk; the only Kirana is 1.8 km from the restaurant, beyond both broadcast rounds
     * (500 m, 1 km), so the groceries are given up and the food goes alone, all the way to the door.
     */
    private Delivered deliverMixedOrderWithoutAnyKiranaInRange() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(0, -300), true);
        Session kirana = signUpMerchant("KIRANA", "Far Kirana", point(0, 1500), true);
        Session rider = onlineRider(point(0, -250));
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String orderId = mixedOrder(customer, restaurant.id(), biryani, milk);

        String offer = messages.waitFor(rider.id(), "/queue/route-updates", "OFFER", "$.data.orderIds[0]", orderId);
        assertThat((List<String>) read(offer, "$.data.stops[*].type")).containsExactly("RESTAURANT", "CUSTOMER");
        String route = post("/api/v1/agent/offers/" + read(offer, "$.data.offerId") + "/accept", rider.token(), null);
        String restaurantStop = read(route, "$.stops[0].stopId");

        post("/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(), "{\"prepMinutes\":1}");
        post("/api/v1/merchant/orders/" + orderId + "/ready", restaurant.token(), null);
        post("/api/v1/agent/route/stops/" + restaurantStop + "/arrived", rider.token(), null);
        String qr = read(get("/api/v1/merchant/store-qr", restaurant.token()), "$.qrPayload");
        post("/api/v1/agent/route/stops/" + restaurantStop + "/scan-qr", rider.token(), "{\"qrPayload\":\"%s\"}".formatted(qr));
        String otp = read(get("/api/v1/customer/orders/" + orderId, customer.token()), "$.deliveryOtp");
        post("/api/v1/agent/orders/" + orderId + "/deliver", rider.token(), "{\"deliveryOtp\":\"%s\"}".formatted(otp));
        return new Delivered(customer, orderId);
    }

    private String mixedOrder(Session customer, UUID restaurantId, String dish, String grocery) throws Exception {
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurantId));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(grocery));
        return read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");
    }
}
