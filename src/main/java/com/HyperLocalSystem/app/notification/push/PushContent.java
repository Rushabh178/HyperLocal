package com.HyperLocalSystem.app.notification.push;

import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.RouteInjectionNotice;
import com.HyperLocalSystem.app.agent.dto.AgentTaskDtos.TaskOffer;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.BroadcastTakenNotice;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.KiranaBroadcast;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrder;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.OrderCancelledNotice;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.RiderArrivingNotice;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import com.HyperLocalSystem.app.realtime.RealtimeMessage;

import java.util.Optional;

/**
 * Which real-time messages also go out as a push, and how they look. Offers are data-only and
 * expire with the offer; the rest show a title and body. Types not listed here are WebSocket-only.
 */
public final class PushContent {

    private PushContent() {
    }

    public static Optional<PushMessage> of(RealtimeMessage message) {
        PushMessage push = switch (message.type()) {
            // ---- rider
            case OFFER -> PushMessage.dataOnly(message, ((TaskOffer) message.data()).expiresAt());
            case STOP_ADDED -> PushMessage.notification(message, "+1 stop added",
                    ((RouteInjectionNotice) message.data()).message());
            // ---- customer
            case ORDER_STATUS -> PushMessage.notification(message, "Order update",
                    customerText((TrackingResponse) message.data()));
            // ---- merchant
            case NEW_ORDER -> {
                MerchantOrder order = (MerchantOrder) message.data();
                int count = order.items().stream().mapToInt(item -> item.quantity()).sum();
                yield PushMessage.notification(message, "New order",
                        count + (count == 1 ? " item" : " items") + " · ₹" + order.amount().toPlainString() + ". Accept it now.");
            }
            case ORDER_CANCELLED -> PushMessage.notification(message, "Order cancelled",
                    Optional.ofNullable(((OrderCancelledNotice) message.data()).reason()).orElse("The order was cancelled"));
            case BROADCAST_OFFER -> PushMessage.dataOnly(message, ((KiranaBroadcast) message.data()).expiresAt());
            case BROADCAST_TAKEN -> PushMessage.notification(message, "Order no longer available",
                    switch (((BroadcastTakenNotice) message.data()).reason()) {
                        case "CLAIMED" -> "Another store got this order first";
                        case "EXPIRED" -> "The order timed out";
                        default -> "The customer cancelled the order";
                    });
            case RIDER_ARRIVING -> PushMessage.notification(message, "Rider arriving",
                    ((RiderArrivingNotice) message.data()).riderName() + " is at your counter");
            case ORDER_PICKED_UP -> PushMessage.notification(message, "Order picked up", "The rider has collected the order");
            default -> null;
        };
        return Optional.ofNullable(push);
    }

    private static String customerText(TrackingResponse tracking) {
        return switch (tracking.status()) {
            case PENDING -> "Waiting for your payment";
            case CONFIRMED -> "Your order is confirmed";
            case BATCHED, PICKING -> "A rider is picking up your order";
            case OUT_FOR_DELIVERY -> "Your order is on the way";
            case COMPLETED -> "Delivered. Enjoy!";
            case CANCELLED -> "Your order was cancelled";
        };
    }
}
