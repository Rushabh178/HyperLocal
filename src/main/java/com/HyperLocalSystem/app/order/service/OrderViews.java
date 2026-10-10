package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.cart.service.BillCalculator;
import com.HyperLocalSystem.app.common.geo.GeoPoint;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.customer.service.CustomerAddressService;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.dto.OrderDtos.MissingItem;
import com.HyperLocalSystem.app.order.dto.OrderDtos.StopsUpdate;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingStop;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingStopStatus;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.repository.OrderItemRepository;
import com.HyperLocalSystem.app.payment.entity.Payment;
import com.HyperLocalSystem.app.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The customer-facing pieces shared by order details, the tracking snapshot and STOPS_UPDATED:
 * the stop line, missing items and the bill after refunds.
 */
@Service
@RequiredArgsConstructor
public class OrderViews {

    private final RouteStopRepository stops;
    private final MerchantRepository merchants;
    private final CustomerAddressRepository addresses;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;

    /**
     * The rider's real stops once someone has the order; before that, the stops the order will have
     * (no {@code stopId} yet).
     */
    @Transactional(readOnly = true)
    public List<TrackingStop> stops(UnifiedOrder order) {
        List<RouteStop> routeStops = stops.findByOrderIdOrderBySequence(order.getId());
        if (!routeStops.isEmpty()) {
            List<TrackingStop> line = new ArrayList<>(routeStops.stream().map(OrderViews::toTrackingStop).toList());
            boolean kiranaOnRoute = routeStops.stream().anyMatch(s -> s.getType() == StopType.KIRANA);
            if (order.getGroceryStatus() == PartStatus.UNFULFILLED && !kiranaOnRoute) {
                line.addFirst(new TrackingStop(null, StopType.KIRANA, "Groceries unavailable", TrackingStopStatus.SKIPPED, null,
                        null, null, null));
            }
            return line;
        }
        List<TrackingStop> planned = new ArrayList<>();
        if (order.hasGroceries()) {
            planned.add(order.getKiranaId() == null
                    ? new TrackingStop(null, StopType.KIRANA, "Finding a store nearby", partStatus(order.getGroceryStatus()), null,
                    null, null, null)
                    : storeStop(StopType.KIRANA, order.getKiranaId(), partStatus(order.getGroceryStatus())));
        }
        if (order.hasFood()) {
            planned.add(storeStop(StopType.RESTAURANT, order.getRestaurantId(), partStatus(order.getFoodStatus())));
        }
        TrackingStopStatus home = switch (order.getStatus()) {
            case COMPLETED -> TrackingStopStatus.DELIVERED;
            case CANCELLED -> TrackingStopStatus.SKIPPED;
            default -> TrackingStopStatus.PENDING;
        };
        GeoPoint drop = GeoUtils.fromPoint(order.getDropLocation());
        String dropAddress = order.getDeliveryAddressId() == null ? null : addresses.findById(order.getDeliveryAddressId())
                .map(a -> CustomerAddressService.toResponse(a).fullAddress()).orElse(null);
        planned.add(new TrackingStop(null, StopType.CUSTOMER, "Home", home, order.getCompletedAt(), drop.lat(), drop.lng(), dropAddress));
        return planned;
    }

    /** Items the rider marked missing at the Kirana, with what goes back to the customer. */
    @Transactional(readOnly = true)
    public List<MissingItem> missingItems(UnifiedOrder order) {
        return orderItems.findByOrderIdOrderByCreatedAt(order.getId()).stream()
                .filter(OrderItem::isDeficient)
                .map(item -> new MissingItem(item.getId(), item.getName(), item.getQuantity(), item.getLineTotal()))
                .toList();
    }

    /** The order's bill with everything refunded so far (missing items, unavailable groceries, cancellation). */
    @Transactional(readOnly = true)
    public BillSummary bill(UnifiedOrder order) {
        BigDecimal refunds = payments.findByOrderId(order.getId()).map(Payment::getRefundedAmount).orElse(BigDecimal.ZERO);
        BigDecimal total = order.getFinancialTotal();
        return new BillSummary(order.getFoodSubtotal(), order.getGrocerySubtotal(), order.getDeliveryFee(),
                order.getConvenienceFee(), order.getTaxes(), order.getDiscount(), total,
                BillCalculator.money(refunds), BillCalculator.money(total.subtract(refunds)));
    }

    /** Data of the STOPS_UPDATED message. */
    @Transactional(readOnly = true)
    public StopsUpdate stopsUpdate(UnifiedOrder order) {
        return new StopsUpdate(order.getId(), order.getStatus(), stops(order), missingItems(order), bill(order));
    }

    private static TrackingStop toTrackingStop(RouteStop stop) {
        TrackingStopStatus status = switch (stop.getState()) {
            case PENDING, EN_ROUTE -> TrackingStopStatus.PENDING;
            case ARRIVED -> TrackingStopStatus.ARRIVED;
            case COMPLETED -> stop.isPickup() ? TrackingStopStatus.PICKED_UP : TrackingStopStatus.DELIVERED;
            case SKIPPED -> TrackingStopStatus.SKIPPED;
        };
        String name = stop.getType() == StopType.CUSTOMER ? "Home" : stop.getName();
        GeoPoint at = GeoUtils.fromPoint(stop.getLocation());
        return new TrackingStop(stop.getId(), stop.getType(), name, status, stop.getCompletedAt(), at.lat(), at.lng(), stop.getAddress());
    }

    private TrackingStop storeStop(StopType type, UUID merchantId, TrackingStopStatus status) {
        Merchant store = merchants.findById(merchantId).orElse(null);
        if (store == null) {
            return new TrackingStop(null, type, "Store", status, null, null, null, null);
        }
        GeoPoint at = GeoUtils.fromPoint(store.getLocation());
        return new TrackingStop(null, type, store.getStoreName(), status, null, at.lat(), at.lng(), store.getAddress());
    }

    private static TrackingStopStatus partStatus(PartStatus status) {
        return switch (status) {
            case PICKED_UP -> TrackingStopStatus.PICKED_UP;
            case UNFULFILLED, CANCELLED -> TrackingStopStatus.SKIPPED;
            default -> TrackingStopStatus.PENDING;
        };
    }

}
