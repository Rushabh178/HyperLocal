package com.HyperLocalSystem.app.merchant;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Phase 6: the restaurant works its kitchen ticket; two Kiranas race for the grocery broadcast. */
class MerchantOrderFlowTest extends IntegrationTest {

    @Test
    void kitchenTicketAndFirstClaimWins() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Spice Hub", point(300, 0), true);
        Session kirana1 = signUpMerchant("KIRANA", "Sharma Kirana", point(300, 200), true);
        Session kirana2 = signUpMerchant("KIRANA", "Gupta Stores", point(300, -300), true);
        String biryani = createItem(restaurant.token(), "Veg Biryani", "180.00", "Mains", "HOT");
        String milk = createItem(kirana1.token(), "Toned Milk", "30.00", "Dairy", "COLD");
        Session customer = signUpCustomer();
        String home = addAddress(customer.token(), point(0, 0));
        post("/api/v1/customer/cart/items", customer.token(),
                "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(biryani, restaurant.id()));
        post("/api/v1/customer/cart/items", customer.token(), "{\"itemType\":\"GROCERY\",\"itemId\":\"%s\",\"quantity\":2}".formatted(milk));
        String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

        // ---- restaurant: NEW → ACCEPTED → DELAYED → READY
        String tickets = get("/api/v1/merchant/orders", restaurant.token());
        assertThat((String) read(tickets, "$.items[0].status")).isEqualTo("NEW");
        assertThat((Double) read(tickets, "$.items[0].amount")).isEqualTo(180.0);
        String accepted = post("/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(), "{\"prepMinutes\":15}");
        Instant deadline = Instant.parse(read(accepted, "$.prepDeadline"));
        String delayed = post("/api/v1/merchant/orders/" + orderId + "/delay", restaurant.token(),
                "{\"additionalMinutes\":10,\"reason\":\"Rush hour\"}");
        assertThat((String) read(delayed, "$.status")).isEqualTo("DELAYED");
        assertThat(Instant.parse(read(delayed, "$.prepDeadline"))).isEqualTo(deadline.plusSeconds(600));
        assertThat((String) read(post("/api/v1/merchant/orders/" + orderId + "/ready", restaurant.token(), null), "$.status"))
                .isEqualTo("READY");
        // Illegal moves are rejected by the state machine; a Kirana can only mark its own claimed orders ready.
        String illegal = call(HttpMethod.POST, "/api/v1/merchant/orders/" + orderId + "/accept", restaurant.token(),
                "{\"prepMinutes\":5}", 409);
        assertThat((String) read(illegal, "$.code")).isEqualTo("ILLEGAL_STATE_TRANSITION");
        call(HttpMethod.POST, "/api/v1/merchant/orders/" + orderId + "/ready", kirana1.token(), null, 404);

        // ---- groceries: confirming the order broadcast them to both Kiranas; both tap at once, exactly one wins.
        await().atMost(Duration.ofSeconds(10)).until(() -> !((List<?>) read(get("/api/v1/merchant/broadcasts", kirana2.token()), "$.items")).isEmpty());
        String seen = get("/api/v1/merchant/broadcasts", kirana2.token());
        UUID broadcastId = UUID.fromString(read(seen, "$.items[0].broadcastId"));
        assertThat((Double) read(seen, "$.items[0].payoutAmount")).isEqualTo(60.0);
        assertThat((List<String>) read(seen, "$.items[0].items[*].name")).containsExactly("Toned Milk");

        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> tap1 = () -> tap(start, kirana1, broadcastId);
        Callable<Boolean> tap2 = () -> tap(start, kirana2, broadcastId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Boolean> r1 = pool.submit(tap1);
        Future<Boolean> r2 = pool.submit(tap2);
        start.countDown();
        boolean won1 = r1.get();
        boolean won2 = r2.get();
        pool.shutdown();
        assertThat(won1 ^ won2).as("exactly one Kirana wins").isTrue();

        Session winner = won1 ? kirana1 : kirana2;
        Session loser = won1 ? kirana2 : kirana1;
        // Claimed = accepted by the Kirana; it marks the bag ready with the same endpoint as restaurants.
        assertThat((String) read(get("/api/v1/merchant/orders", winner.token()), "$.items[0].status")).isEqualTo("ACCEPTED");
        assertThat((String) read(post("/api/v1/merchant/orders/" + orderId + "/ready", winner.token(), null), "$.status"))
                .isEqualTo("READY");
        assertThat((Integer) read(get("/api/v1/merchant/orders", loser.token()), "$.totalItems")).isZero();
        assertThat((List<?>) read(get("/api/v1/merchant/broadcasts", loser.token()), "$.items")).isEmpty();
    }

    private boolean tap(CountDownLatch start, Session kirana, UUID broadcastId) throws Exception {
        start.await();
        return read(post("/api/v1/merchant/broadcasts/" + broadcastId + "/claim", kirana.token(), null), "$.claimed");
    }
}
