package com.HyperLocalSystem.app.dispatch;

import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastOffer;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastStatus;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastTarget;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastOfferRepository;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastTargetRepository;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.merchant.repository.NearbyMerchant;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.KiranaClaimedEvent;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crowdsourced inventory claim (blueprint §3 step 3 and the "Silent Broadcast Timeout" edge case).
 * Each round is a broadcast_offers row with its targets in broadcast_targets; the first targeted
 * Kirana to tap "Items in Hand" wins through an atomic conditional UPDATE.
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
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    /** Starts round 1 for the order's groceries now. Returns the round, or empty targets if no Kirana is open nearby. */
    @Transactional
    public BroadcastOffer broadcast(UUID orderId) {
        UnifiedOrder order = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        BroadcastOffer offer = schedule(order, 1, Instant.now());
        open(offer);
        return offer;
    }

    /** Creates a round that the poller (or the caller) opens at {@code startsAt}. */
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
        return offers.save(offer);
    }

    /**
     * Pings the {@code ops.kiranaBroadcastFanout} (5) nearest open Kiranas not pinged in earlier
     * rounds, within this round's radius (500 m, then 1 km, ...). Returns false when nobody is in range.
     */
    @Transactional
    public boolean open(BroadcastOffer offer) {
        AppProperties.Ops ops = properties.ops();
        int radius = radiusForRound(offer.getRound());
        List<NearbyMerchant> kiranas = targets.findNextKiranas(
                spatialIndex.cellsWithin(offer.getOriginLat(), offer.getOriginLng(), radius), MerchantType.KIRANA.name(),
                offer.getOriginLat(), offer.getOriginLng(), radius, offer.getOrderId(), ops.kiranaBroadcastFanout());
        Instant now = Instant.now();
        offer.setStatus(BroadcastStatus.OPEN);
        offer.setExpiresAt(kiranas.isEmpty() ? now : now.plus(ops.kiranaClaimTimeout()));
        kiranas.forEach(k -> targets.save(new BroadcastTarget(offer.getId(), k.getId(), (int) Math.round(k.getDistanceMeters()))));
        log.info("Broadcast round {} for order {}: {} Kiranas within {} m", offer.getRound(), offer.getOrderId(), kiranas.size(), radius);
        // TODO(edge-case): push the broadcast to the Kiranas' phones (FCM); for now they poll GET /merchant/broadcasts.
        return !kiranas.isEmpty();
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
        order.moveGroceryTo(PartStatus.READY);
        events.publishEvent(new KiranaClaimedEvent(order.getId(), merchantId));
        return true;
    }

    @Transactional(readOnly = true)
    public List<BroadcastOffer> openFor(UUID merchantId) {
        return offers.findOpenFor(merchantId, Instant.now());
    }

    /**
     * No claim in 60 s: expand to the next H3 ring and ping the next 5 closest shops. If that fails
     * too, the grocery part becomes UNFULFILLED.
     */
    public void onClaimTimeout(UUID broadcastId) {
        throw new NotImplementedException("Broadcast ring expansion");
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
