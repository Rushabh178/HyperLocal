package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.BroadcastCloseReason;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.BroadcastClosedEvent;
import com.HyperLocalSystem.app.dispatch.DispatchEvents.BroadcastOpenedEvent;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastOffer;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastStatus;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastTarget;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastOfferRepository;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastTargetRepository;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.merchant.repository.NearbyMerchant;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.KiranaClaimedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.order.service.OrderLifecycle;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crowdsourced inventory claim (blueprint §3 step 3 and the "Silent Broadcast Timeout" edge case).
 * Each round is a broadcast_offers row with its targets in broadcast_targets. Round timing lives in
 * the row and {@link DispatchScheduler} advances due rounds, so no in-memory timer is involved and
 * any instance can do the work. The first targeted Kirana to tap "Items in Hand" wins through an
 * atomic conditional UPDATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiranaBroadcastService {

    private final BroadcastOfferRepository offers;
    private final BroadcastTargetRepository targets;
    private final UnifiedOrderRepository orders;
    private final MerchantRepository merchants;
    private final SpatialIndexService spatialIndex;
    private final OrderLifecycle lifecycle;
    private final PaymentService paymentService;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    /** Creates a round that opens at {@code startsAt}; opened right away when that is already due. */
    @Transactional
    public BroadcastOffer schedule(UnifiedOrder order, int round, Instant startsAt) {
        GeoPoint origin = origin(order);
        BroadcastOffer offer = new BroadcastOffer();
        offer.setOrderId(order.getId());
        offer.setRound(round);
        offer.setStatus(BroadcastStatus.SCHEDULED);
        offer.setOriginLat(origin.lat());
        offer.setOriginLng(origin.lng());
        offer.setStartsAt(startsAt);
        offers.save(offer);
        if (!startsAt.isAfter(Instant.now())) {
            advance(offer);
        }
        return offer;
    }

    /**
     * Moves a due round forward: a SCHEDULED round opens (or, with nobody in range, rolls straight on);
     * an expired OPEN round gives way to the next, wider round; after the last round the grocery part
     * is UNFULFILLED. Rounds of cancelled / already-claimed orders are simply closed.
     */
    @Transactional
    public void advance(BroadcastOffer offer) {
        UnifiedOrder order = orders.findById(offer.getOrderId()).orElseThrow();
        boolean wasOpen = offer.getStatus() == BroadcastStatus.OPEN;
        if (order.getGroceryStatus() != PartStatus.BROADCASTING || !OrderStatus.ACTIVE.contains(order.getStatus())) {
            offer.setStatus(BroadcastStatus.CANCELLED);
            if (wasOpen) {
                events.publishEvent(new BroadcastClosedEvent(offer.getId(), null, BroadcastCloseReason.CANCELLED));
            }
            return;
        }
        if (offer.getStatus() == BroadcastStatus.SCHEDULED && open(offer)) {
            return;
        }
        offer.setStatus(BroadcastStatus.EXPIRED);
        if (wasOpen) {
            events.publishEvent(new BroadcastClosedEvent(offer.getId(), null, BroadcastCloseReason.EXPIRED));
        }
        if (offer.getRound() >= properties.ops().kiranaBroadcastMaxRounds()) {
            unfulfilled(order);
        } else {
            schedule(order, offer.getRound() + 1, Instant.now());
        }
    }

    /**
     * Pings the {@code ops.kiranaBroadcastFanout} (5) nearest open Kiranas not pinged in earlier
     * rounds, within this round's radius (500 m, then 1 km, ...: the H3 disk widens with it).
     * Returns false when nobody is in range.
     */
    private boolean open(BroadcastOffer offer) {
        AppProperties.Ops ops = properties.ops();
        int radius = radiusForRound(offer.getRound());
        List<NearbyMerchant> kiranas = targets.findNextKiranas(
                spatialIndex.cellsWithin(offer.getOriginLat(), offer.getOriginLng(), radius), MerchantType.KIRANA.name(),
                offer.getOriginLat(), offer.getOriginLng(), radius, offer.getOrderId(), ops.kiranaBroadcastFanout());
        log.info("Broadcast round {} for order {}: {} Kiranas within {} m", offer.getRound(), offer.getOrderId(), kiranas.size(), radius);
        if (kiranas.isEmpty()) {
            return false;
        }
        offer.setStatus(BroadcastStatus.OPEN);
        offer.setExpiresAt(Instant.now().plus(ops.kiranaClaimTimeout()));
        kiranas.forEach(k -> targets.save(new BroadcastTarget(offer.getId(), k.getId(), (int) Math.round(k.getDistanceMeters()))));
        events.publishEvent(new BroadcastOpenedEvent(offer.getId()));
        return true;
    }

    /**
     * No Kirana anywhere in range: a grocery-only order is cancelled (money back); a combined order
     * carries on with the food and the grocery amount goes back to the wallet.
     * TODO(edge-case): fall back to the dark-store hub before giving up; recompute the delivery fee.
     */
    private void unfulfilled(UnifiedOrder order) {
        log.info("Groceries of order {} unfulfilled: no Kirana claimed them", order.getId());
        order.moveGroceryTo(PartStatus.UNFULFILLED);
        if (!order.hasFood()) {
            lifecycle.cancel(order, "No store nearby had these items");
        } else {
            paymentService.partialRefundToWallet(order.getId(), order.getGrocerySubtotal(),
                    "Groceries unavailable nearby", "refund:groceries:" + order.getId());
        }
    }

    /**
     * First tap on "Items in Hand" wins: an atomic conditional UPDATE decides, so two Kiranas
     * tapping at the same moment can never both get the order. The winner becomes the order's Kirana.
     */
    @Transactional
    public boolean claim(UUID broadcastId, UUID merchantId) {
        if (offers.claim(broadcastId, merchantId, Instant.now()) != 1) {
            return false;
        }
        BroadcastOffer offer = offers.findById(broadcastId).orElseThrow();
        UnifiedOrder order = orders.findById(offer.getOrderId()).orElseThrow();
        order.setKiranaId(merchantId);
        order.moveGroceryTo(PartStatus.CLAIMED);
        events.publishEvent(new KiranaClaimedEvent(order.getId(), merchantId));
        events.publishEvent(new BroadcastClosedEvent(broadcastId, merchantId, BroadcastCloseReason.CLAIMED));
        return true;
    }

    @Transactional(readOnly = true)
    public List<BroadcastOffer> openFor(UUID merchantId) {
        return offers.findOpenFor(merchantId, Instant.now());
    }

    /** The kitchen is late: rounds that haven't started yet start later too. */
    @Transactional
    public void postpone(UUID orderId, Duration delay) {
        offers.findByOrderIdAndStatusIn(orderId, List.of(BroadcastStatus.SCHEDULED))
                .forEach(offer -> offer.setStartsAt(offer.getStartsAt().plus(delay)));
        // TODO(edge-case): if a Kirana already claimed it, push the rider's Kirana stop back instead.
    }

    /** The order was cancelled: close its rounds so no Kirana can claim it any more. */
    @Transactional
    public void cancelFor(UUID orderId) {
        offers.findByOrderIdAndStatusIn(orderId, List.of(BroadcastStatus.SCHEDULED, BroadcastStatus.OPEN)).forEach(offer -> {
            if (offer.getStatus() == BroadcastStatus.OPEN) {
                events.publishEvent(new BroadcastClosedEvent(offer.getId(), null, BroadcastCloseReason.CANCELLED));
            }
            offer.setStatus(BroadcastStatus.CANCELLED);
        });
    }

    int radiusForRound(int round) {
        AppProperties.Ops ops = properties.ops();
        return Math.min(ops.kiranaBroadcastRadiusMeters() * round, ops.serviceRadiusMeters());
    }

    /** Combined orders: near the restaurant (the rider passes by). Grocery-only: near the customer. */
    private GeoPoint origin(UnifiedOrder order) {
        if (order.getRestaurantId() != null) {
            return merchants.findById(order.getRestaurantId())
                    .map(restaurant -> GeoUtils.fromPoint(restaurant.getLocation()))
                    .orElseThrow(() -> ApiException.notFound("Restaurant"));
        }
        return GeoUtils.fromPoint(order.getDropLocation());
    }
}
