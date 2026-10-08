package com.HyperLocalSystem.app.realtime;

import com.HyperLocalSystem.app.support.IntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** A real STOMP client over a real socket: CONNECT with a token on /ws, then receive a merchant event. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompClientTest extends IntegrationTest {

    private static final String QUEUE = "/user/queue/merchant-events";

    @LocalServerPort
    private int port;

    @Autowired
    private SimpUserRegistry userRegistry;

    @Test
    void merchantReceivesNewOrderOverTheSocket() throws Exception {
        Session restaurant = signUpMerchant("RESTAURANT", "Socket Dhaba", point(300, 0), true);

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + restaurant.token());
        StompSession stomp = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connect,
                new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        try {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            stomp.subscribe(QUEUE, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return byte[].class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            });
            // The server has the subscription before anything is published to it.
            Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
                SimpUser user = userRegistry.getUser(restaurant.id().toString());
                return user != null && user.getSessions().stream()
                        .anyMatch(s -> s.getSubscriptions().stream().anyMatch(sub -> QUEUE.equals(sub.getDestination())));
            });

            String dish = createItem(restaurant.token(), "Paneer Tikka", "220.00", "Starters", "HOT");
            Session customer = signUpCustomer();
            String home = addAddress(customer.token(), point(0, 0));
            post("/api/v1/customer/cart/items", customer.token(),
                    "{\"itemType\":\"FOOD\",\"itemId\":\"%s\",\"restaurantId\":\"%s\",\"quantity\":1}".formatted(dish, restaurant.id()));
            String orderId = read(call(HttpMethod.POST, "/api/v1/customer/orders/checkout", customer.token(),
                    "{\"addressId\":\"%s\",\"paymentMethod\":\"UPI\"}".formatted(home), 201), "$.orderId");

            String event = received.poll(15, TimeUnit.SECONDS);
            assertThat(event).as("NEW_ORDER over the socket").isNotNull();
            assertThat((String) read(event, "$.type")).isEqualTo("NEW_ORDER");
            assertThat((String) read(event, "$.data.orderId")).isEqualTo(orderId);
        } finally {
            stomp.disconnect();
            client.stop();
        }
    }
}
