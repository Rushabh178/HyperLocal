package com.HyperLocalSystem.app.order.service;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartItemResponse;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartItemType;
import com.HyperLocalSystem.app.cart.service.BillCalculator;
import com.HyperLocalSystem.app.cart.service.CartService;
import com.HyperLocalSystem.app.cart.service.CartService.PricedCart;
import com.HyperLocalSystem.app.cart.service.CartService.PricedLine;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.StopState;
import com.HyperLocalSystem.app.common.model.StopType;
import com.HyperLocalSystem.app.customer.entity.CustomerAddress;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.customer.service.CustomerAddressService;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.dto.OrderDtos.AgentInfo;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CancelOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderDetail;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderSummary;
import com.HyperLocalSystem.app.order.dto.OrderDtos.RateOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TimelineResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TimelineStop;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import com.HyperLocalSystem.app.order.entity.OrderRating;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.OrderPlacedEvent;
import com.HyperLocalSystem.app.order.repository.OrderItemRepository;
import com.HyperLocalSystem.app.order.repository.OrderRatingRepository;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import com.HyperLocalSystem.app.payment.entity.Payment;
import com.HyperLocalSystem.app.payment.repository.PaymentRepository;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import com.HyperLocalSystem.app.security.Hashing;
import com.HyperLocalSystem.app.spatial.SpatialIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerOrderService {

    private static final Set<OrderStatus> CANCELLABLE =
            EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.BATCHED, OrderStatus.PICKING);

    private final UnifiedOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderRatingRepository ratings;
    private final CustomerAddressRepository addresses;
    private final MerchantRepository merchants;
    private final DeliveryAgentRepository agents;
    private final PaymentRepository payments;
    private final CartService cartService;
    private final PaymentService paymentService;
    private final OrderLifecycle lifecycle;
    private final SpatialIndexService spatialIndex;
    private final RouteStopRepository routeStopRepository;
    private final TrackingService trackingService;
    private final OrderViews orderViews;
    private final ApplicationEventPublisher events;

    /**
     * Blueprint step 1 "Consolidated Cart": re-validate and re-price the cart, freeze it into an
     * order with line items, take the payment (UPI hold or wallet), clear the cart. Retrying with
     * the same Idempotency-Key returns the order created the first time.
     */
    @Transactional
    public CheckoutResponse checkout(UUID customerId, CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var previous = orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey.trim());
            if (previous.isPresent()) {
                return toCheckoutResponse(previous.get());
            }
        }
        PricedCart cart = cartService.lockAndPrice(customerId);
        if (cart.isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Your cart is empty");
        }
        CustomerAddress address = addresses.findByIdAndCustomerIdAndDeletedFalse(request.addressId(), customerId)
                .orElseThrow(() -> ApiException.notFound("Address"));
        cartService.ensureServiceable(cart, address);
        ensureOrderable(cart);

        UnifiedOrder order = new UnifiedOrder();
        order.setCustomerId(customerId);
        order.setRestaurantId(cart.food().isEmpty() ? null : cart.cart().getRestaurantId());
        order.initParts(!cart.food().isEmpty(), !cart.groceries().isEmpty());
        order.setDeliveryAddressId(address.getId());
        order.setDropLocation(address.getLocation());
        order.setDropH3(spatialIndex.cellOf(address.getLocation().getY(), address.getLocation().getX()));
        BillSummary bill = cart.bill();
        order.setFoodSubtotal(bill.foodSubtotal());
        order.setGrocerySubtotal(bill.grocerySubtotal());
        order.setDeliveryFee(bill.deliveryFee());
        order.setConvenienceFee(bill.convenienceFee());
        order.setTaxes(bill.taxes());
        order.setDiscount(bill.discount());
        order.setFinancialTotal(bill.total());
        order.setPaymentMethod(request.paymentMethod());
        order.setDeliveryOtp(Hashing.randomDigits(4));
        order.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim());
        orders.saveAndFlush(order);

        List<OrderItem> lines = new ArrayList<>();
        cart.food().forEach(line -> lines.add(toOrderItem(order.getId(), line)));
        cart.groceries().forEach(line -> lines.add(toOrderItem(order.getId(), line)));
        orderItems.saveAll(lines);

        Payment payment = paymentService.start(order.getId(), customerId, bill.total(), request.paymentMethod(), request.upiVpa());
        if (payment.isSecured()) {
            lifecycle.confirm(order);
        }
        cartService.clear(customerId);
        events.publishEvent(new OrderPlacedEvent(order.getId(), customerId));
        return new CheckoutResponse(order.getId(), order.getStatus(), order.getFinancialTotal(), PaymentService.toResponse(payment));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummary> list(UUID customerId, OrderStatus status, int page, int size) {
        var pageable = PageResponse.request(page, size);
        var result = status == null
                ? orders.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                : orders.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, status, pageable);
        return PageResponse.of(result, o -> new OrderSummary(o.getId(), o.getStatus(), o.hasFood(), o.hasGroceries(),
                o.getFinancialTotal(), o.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public OrderDetail get(UUID customerId, UUID orderId) {
        return toDetail(load(customerId, orderId));
    }

    /**
     * Single line Kirana → Restaurant → Home; icons light up as the rider progresses. Once a rider has
     * the order, the line is the rider's actual stops (with ETAs); before that it is built from the
     * order's parts.
     */
    @Transactional(readOnly = true)
    public TimelineResponse timeline(UUID customerId, UUID orderId) {
        UnifiedOrder order = load(customerId, orderId);
        List<RouteStop> routeStops = routeStopRepository.findByOrderIdOrderBySequence(orderId);
        if (!routeStops.isEmpty()) {
            List<TimelineStop> line = routeStops.stream()
                    .map(s -> new TimelineStop(s.getType(), s.getType() == StopType.CUSTOMER ? "Home" : s.getName(),
                            s.getState(), s.getEta(), s.getCompletedAt()))
                    .toList();
            Instant deliveredBy = routeStops.stream().filter(s -> s.getType() == StopType.CUSTOMER)
                    .map(RouteStop::getEta).findFirst().orElse(null);
            return new TimelineResponse(order.getId(), order.getStatus(), line, deliveredBy);
        }
        List<TimelineStop> stops = new ArrayList<>();
        if (order.hasGroceries()) {
            String name = order.getKiranaId() == null ? "Finding a store nearby" : storeName(order.getKiranaId());
            stops.add(new TimelineStop(StopType.KIRANA, name, partState(order.getGroceryStatus()), null, null));
        }
        if (order.hasFood()) {
            stops.add(new TimelineStop(StopType.RESTAURANT, storeName(order.getRestaurantId()),
                    partState(order.getFoodStatus()), order.getPrepReadyAt(), null));
        }
        StopState home = switch (order.getStatus()) {
            case COMPLETED -> StopState.COMPLETED;
            case CANCELLED -> StopState.SKIPPED;
            case OUT_FOR_DELIVERY -> StopState.EN_ROUTE;
            default -> StopState.PENDING;
        };
        stops.add(new TimelineStop(StopType.CUSTOMER, "Home", home, null, order.getCompletedAt()));
        return new TimelineResponse(order.getId(), order.getStatus(), stops, null);
    }

    @Transactional(readOnly = true)
    public TrackingResponse tracking(UUID customerId, UUID orderId) {
        return trackingService.snapshot(load(customerId, orderId));
    }

    /** Only before anything has been picked up. Releases the hold / refunds the wallet and stops dispatch. */
    @Transactional
    public OrderDetail cancel(UUID customerId, UUID orderId, CancelOrderRequest request) {
        UnifiedOrder order = load(customerId, orderId);
        if (!CANCELLABLE.contains(order.getStatus())) {
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE", "This order can no longer be cancelled");
        }
        if (order.getFoodStatus() == PartStatus.PICKED_UP || order.getGroceryStatus() == PartStatus.PICKED_UP) {
            throw ApiException.conflict("ORDER_ALREADY_PICKED_UP", "The rider has already picked up part of this order");
        }
        lifecycle.cancel(order, request.reason().trim());
        return toDetail(order);
    }

    /** Once per completed order; feeds the restaurant's / Kirana's average rating. */
    @Transactional
    public void rate(UUID customerId, UUID orderId, RateOrderRequest request) {
        UnifiedOrder order = load(customerId, orderId);
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw ApiException.conflict("ORDER_NOT_COMPLETED", "You can rate an order after it is delivered");
        }
        if (ratings.existsById(orderId)) {
            throw ApiException.conflict("ALREADY_RATED", "You have already rated this order");
        }
        OrderRating rating = new OrderRating();
        rating.setOrderId(orderId);
        rating.setCustomerId(customerId);
        rating.setDeliveryRating(request.deliveryRating());
        rating.setRestaurantRating(order.getRestaurantId() == null ? null : request.restaurantRating());
        rating.setKiranaRating(order.getKiranaId() == null ? null : request.kiranaRating());
        rating.setComment(request.comment());
        ratings.save(rating);
        if (rating.getRestaurantRating() != null) {
            merchants.addRating(order.getRestaurantId(), rating.getRestaurantRating());
        }
        if (rating.getKiranaRating() != null) {
            merchants.addRating(order.getKiranaId(), rating.getKiranaRating());
        }
    }

    // ----------------------------------------------------------------- helpers

    private UnifiedOrder load(UUID customerId, UUID orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    /** Items sold out or the restaurant closed since they were added to the cart. */
    private void ensureOrderable(PricedCart cart) {
        cart.food().stream().map(PricedLine::item).filter(item -> !item.isAvailable()).findFirst().ifPresent(item -> {
            throw ApiException.conflict("ITEM_UNAVAILABLE", item.getName() + " is no longer available");
        });
        cart.groceries().stream().map(PricedLine::item).filter(item -> !item.isAvailable()).findFirst().ifPresent(item -> {
            throw ApiException.conflict("ITEM_UNAVAILABLE", item.getName() + " is no longer available");
        });
        if (!cart.food().isEmpty()) {
            Merchant restaurant = merchants.findById(cart.cart().getRestaurantId())
                    .orElseThrow(() -> ApiException.notFound("Restaurant"));
            if (!restaurant.isOpen()) {
                throw ApiException.conflict("STORE_CLOSED", restaurant.getStoreName() + " is closed right now");
            }
        }
    }

    private static OrderItem toOrderItem(UUID orderId, PricedLine line) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setItemId(line.item().getId());
        item.setType(line.item().getType());
        item.setName(line.item().getName());
        item.setPackSize(line.item().getPackSize());
        item.setUnitPrice(line.item().getPrice());
        item.setQuantity(line.line().getQuantity());
        item.setLineTotal(BillCalculator.money(line.lineTotal()));
        item.setZone(line.item().getZone());
        return item;
    }

    private CheckoutResponse toCheckoutResponse(UnifiedOrder order) {
        Payment payment = payments.findByOrderId(order.getId()).orElseThrow(() -> ApiException.notFound("Payment"));
        return new CheckoutResponse(order.getId(), order.getStatus(), order.getFinancialTotal(), PaymentService.toResponse(payment));
    }

    private OrderDetail toDetail(UnifiedOrder order) {
        List<OrderItem> lines = orderItems.findByOrderIdOrderByCreatedAt(order.getId());
        BillSummary bill = orderViews.bill(order);
        var address = order.getDeliveryAddressId() == null ? null
                : addresses.findById(order.getDeliveryAddressId()).map(CustomerAddressService::toResponse).orElse(null);
        AgentInfo agent = order.getDeliveryAgentId() == null ? null
                : agents.findById(order.getDeliveryAgentId()).map(CustomerOrderService::toAgentInfo).orElse(null);
        boolean otpVisible = OrderStatus.ACTIVE.contains(order.getStatus());
        return new OrderDetail(order.getId(), order.getStatus(),
                lines.stream().filter(l -> l.getType() == ItemType.FOOD).map(CustomerOrderService::toLine).toList(),
                lines.stream().filter(l -> l.getType() == ItemType.GROCERY).map(CustomerOrderService::toLine).toList(),
                bill, address, agent, otpVisible ? order.getDeliveryOtp() : null, orderViews.stops(order), order.getCreatedAt());
    }

    static CartItemResponse toLine(OrderItem item) {
        return new CartItemResponse(item.getId(), CartItemType.valueOf(item.getType().name()), item.getItemId(),
                item.getName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal(), item.getZone());
    }

    private static AgentInfo toAgentInfo(DeliveryAgent agent) {
        String phone = agent.getPhone();
        String masked = phone == null || phone.length() < 4 ? phone : "XXXXXX" + phone.substring(phone.length() - 4);
        return new AgentInfo(agent.getName(), masked, agent.getVehicleNumber(), GeoUtils.fromPoint(agent.getCurrentLocation()));
    }

    private String storeName(UUID merchantId) {
        return merchants.findById(merchantId).map(Merchant::getStoreName).orElse("Store");
    }

    private static StopState partState(PartStatus status) {
        return switch (status) {
            case PICKED_UP -> StopState.COMPLETED;
            case UNFULFILLED, CANCELLED -> StopState.SKIPPED;
            default -> StopState.PENDING;
        };
    }
}
