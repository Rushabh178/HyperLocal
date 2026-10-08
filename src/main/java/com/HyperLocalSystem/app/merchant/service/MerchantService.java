package com.HyperLocalSystem.app.merchant.service;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.repository.DeliveryAgentRepository;
import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.catalog.repository.CatalogItemRepository;
import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.dispatch.KiranaBroadcastService;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastOffer;
import com.HyperLocalSystem.app.dispatch.entity.BroadcastTarget;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastOfferRepository;
import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import com.HyperLocalSystem.app.dispatch.repository.BroadcastTargetRepository;
import com.HyperLocalSystem.app.dispatch.repository.RouteStopRepository;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.AcceptOrderRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.BroadcastItem;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.CatalogItemResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.ClaimResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.KiranaBroadcast;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrder;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantOrderStatus;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.MerchantProfileResponse;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.PrepDelayRequest;
import com.HyperLocalSystem.app.merchant.dto.MerchantDtos.StoreQrResponse;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import com.HyperLocalSystem.app.order.event.OrderEvents.KitchenDelayedEvent;
import com.HyperLocalSystem.app.order.repository.OrderItemRepository;
import com.HyperLocalSystem.app.order.repository.UnifiedOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchants;
    private final CatalogItemRepository catalogItems;
    private final UnifiedOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final DeliveryAgentRepository agents;
    private final BroadcastOfferRepository broadcastOffers;
    private final BroadcastTargetRepository broadcastTargets;
    private final RouteStopRepository routeStops;
    private final KiranaBroadcastService broadcastService;
    private final StoreQrService storeQrService;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------------------- profile

    @Transactional(readOnly = true)
    public MerchantProfileResponse profile(UUID merchantId) {
        return toResponse(load(merchantId));
    }

    /** Only approved stores can open; closing is always allowed. */
    @Transactional
    public MerchantProfileResponse setOpen(UUID merchantId, boolean open) {
        Merchant merchant = load(merchantId);
        if (open && merchant.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.forbidden("MERCHANT_NOT_APPROVED", "Your store must be approved before it can open");
        }
        merchant.setOpen(open);
        return toResponse(merchant);
    }

    // ---- Kirana ----

    /** Open broadcast rounds this Kirana was pinged for (a handful at most). */
    @Transactional(readOnly = true)
    public List<KiranaBroadcast> openBroadcasts(UUID merchantId) {
        requireType(load(merchantId), MerchantType.KIRANA);
        List<BroadcastOffer> open = broadcastService.openFor(merchantId);
        List<UUID> orderIds = open.stream().map(BroadcastOffer::getOrderId).toList();
        Map<UUID, List<OrderItem>> items = itemsByOrder(orderIds, ItemType.GROCERY);
        Map<UUID, UnifiedOrder> byId = orders.findAllById(orderIds).stream()
                .collect(Collectors.toMap(UnifiedOrder::getId, Function.identity()));
        return open.stream().map(offer -> toBroadcast(offer, byId.get(offer.getOrderId()),
                items.getOrDefault(offer.getOrderId(), List.of()), merchantId)).toList();
    }

    /** One broadcast as a given Kirana sees it (used for the BROADCAST_OFFER message too). */
    @Transactional(readOnly = true)
    public KiranaBroadcast broadcastView(BroadcastOffer offer, UUID merchantId) {
        UnifiedOrder order = orders.findById(offer.getOrderId()).orElseThrow();
        return toBroadcast(offer, order, orderItems.findByOrderIdAndTypeOrderByCreatedAt(order.getId(), ItemType.GROCERY), merchantId);
    }

    private KiranaBroadcast toBroadcast(BroadcastOffer offer, UnifiedOrder order, List<OrderItem> items, UUID merchantId) {
        return new KiranaBroadcast(offer.getId(), offer.getOrderId(), toBroadcastItems(items), order.getGrocerySubtotal(),
                broadcastTargets.findByOfferIdAndMerchantId(offer.getId(), merchantId).map(BroadcastTarget::getDistanceMeters).orElse(0),
                offer.getExpiresAt());
    }

    /** The order card for the restaurant (FOOD) or the Kirana (GROCERY), as in GET /merchant/orders. */
    @Transactional(readOnly = true)
    public MerchantOrder orderView(UnifiedOrder order, ItemType type) {
        return toMerchantOrder(order, type, orderItems.findByOrderIdAndTypeOrderByCreatedAt(order.getId(), type));
    }

    /** The single "Items in Hand" button. First tap wins; everyone else gets {@code claimed=false}. */
    @Transactional
    public ClaimResponse claimBroadcast(UUID merchantId, UUID broadcastId) {
        requireType(load(merchantId), MerchantType.KIRANA);
        BroadcastOffer offer = broadcastOffers.findById(broadcastId).orElseThrow(() -> ApiException.notFound("Broadcast"));
        boolean won = broadcastService.claim(broadcastId, merchantId);
        return new ClaimResponse(broadcastId, offer.getOrderId(), won,
                won ? "The order is yours. Keep the items ready for the rider." : "Another store got this order first");
    }

    /** Short-lived signed QR shown on the store's phone (Kirana and restaurant); the rider scans it at pickup. */
    @Transactional(readOnly = true)
    public StoreQrResponse storeQr(UUID merchantId) {
        load(merchantId);
        StoreQrService.SignedQr qr = storeQrService.issue(merchantId);
        return new StoreQrResponse(qr.payload(), qr.expiresAt());
    }

    // ---- orders (both types) ----

    /** Restaurants see their kitchen tickets, Kiranas the orders they claimed. Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<MerchantOrder> orders(UUID merchantId, MerchantOrderStatus status, int page, int size) {
        Merchant merchant = load(merchantId);
        Pageable pageable = PageResponse.request(page, size);
        Set<PartStatus> statuses = status == null ? VISIBLE : partStatuses(status);
        Page<UnifiedOrder> result = merchant.getType() == MerchantType.RESTAURANT
                ? orders.findByRestaurantIdAndFoodStatusInOrderByCreatedAtDesc(merchantId, statuses, pageable)
                : orders.findByKiranaIdAndGroceryStatusInOrderByCreatedAtDesc(merchantId, statuses, pageable);
        ItemType type = merchant.getType() == MerchantType.RESTAURANT ? ItemType.FOOD : ItemType.GROCERY;
        Map<UUID, List<OrderItem>> items = itemsByOrder(result.getContent().stream().map(UnifiedOrder::getId).toList(), type);
        return PageResponse.of(result, order -> toMerchantOrder(order, type, items.getOrDefault(order.getId(), List.of())));
    }

    /** Restaurant accepts and starts the prep timer the rider's arrival is synced to. */
    @Transactional
    public MerchantOrder accept(UUID merchantId, UUID orderId, AcceptOrderRequest request) {
        UnifiedOrder order = loadKitchenOrder(merchantId, orderId);
        order.moveFoodTo(PartStatus.ACCEPTED);
        order.setPrepReadyAt(Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(request.prepMinutes())));
        return toMerchantOrder(order, ItemType.FOOD, orderItems.findByOrderIdAndTypeOrderByCreatedAt(orderId, ItemType.FOOD));
    }

    /** Kitchen bottleneck: pushes the prep deadline back and holds back the Kirana broadcast. */
    @Transactional
    public MerchantOrder reportDelay(UUID merchantId, UUID orderId, PrepDelayRequest request) {
        UnifiedOrder order = loadKitchenOrder(merchantId, orderId);
        Duration delay = Duration.ofMinutes(request.additionalMinutes());
        order.moveFoodTo(PartStatus.DELAYED);
        Instant base = order.getPrepReadyAt() == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS) : order.getPrepReadyAt();
        order.setPrepReadyAt(base.plus(delay));
        // TODO(edge-case): keep the delay reason for ops reporting.
        events.publishEvent(new KitchenDelayedEvent(orderId, delay));
        return toMerchantOrder(order, ItemType.FOOD, orderItems.findByOrderIdAndTypeOrderByCreatedAt(orderId, ItemType.FOOD));
    }

    /** Restaurant: food ready (ACCEPTED/DELAYED → READY). Kirana: groceries packed (CLAIMED → READY). */
    @Transactional
    public MerchantOrder markReady(UUID merchantId, UUID orderId) {
        if (load(merchantId).getType() == MerchantType.KIRANA) {
            UnifiedOrder order = orders.findByIdAndKiranaId(orderId, merchantId).orElseThrow(() -> ApiException.notFound("Order"));
            order.moveGroceryTo(PartStatus.READY);
            return toMerchantOrder(order, ItemType.GROCERY, orderItems.findByOrderIdAndTypeOrderByCreatedAt(orderId, ItemType.GROCERY));
        }
        UnifiedOrder order = loadKitchenOrder(merchantId, orderId);
        order.moveFoodTo(PartStatus.READY);
        return toMerchantOrder(order, ItemType.FOOD, orderItems.findByOrderIdAndTypeOrderByCreatedAt(orderId, ItemType.FOOD));
    }

    private UnifiedOrder loadKitchenOrder(UUID merchantId, UUID orderId) {
        requireType(load(merchantId), MerchantType.RESTAURANT);
        return orders.findByIdAndRestaurantId(orderId, merchantId).orElseThrow(() -> ApiException.notFound("Order"));
    }

    private MerchantOrder toMerchantOrder(UnifiedOrder order, ItemType type, List<OrderItem> items) {
        PartStatus part = type == ItemType.FOOD ? order.getFoodStatus() : order.getGroceryStatus();
        BigDecimal amount = type == ItemType.FOOD ? order.getFoodSubtotal() : order.getGrocerySubtotal();
        String riderName = order.getDeliveryAgentId() == null ? null
                : agents.findById(order.getDeliveryAgentId()).map(DeliveryAgent::getName).orElse(null);
        UUID merchantId = type == ItemType.FOOD ? order.getRestaurantId() : order.getKiranaId();
        Instant riderEta = order.getDeliveryAgentId() == null ? null
                : routeStops.findFirstByOrderIdAndMerchantId(order.getId(), merchantId).map(RouteStop::getEta).orElse(null);
        return new MerchantOrder(order.getId(), merchantStatus(part), toBroadcastItems(items), amount,
                order.getPrepReadyAt(), riderName, riderEta);
    }

    private Map<UUID, List<OrderItem>> itemsByOrder(List<UUID> orderIds, ItemType type) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItems.findByOrderIdInOrderByCreatedAt(orderIds).stream()
                .filter(item -> item.getType() == type)
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private static List<BroadcastItem> toBroadcastItems(List<OrderItem> items) {
        return items.stream().map(i -> new BroadcastItem(i.getName(), i.getPackSize(), i.getQuantity())).toList();
    }

    /** Merchants never see orders still awaiting payment or groceries still being broadcast. */
    private static final Set<PartStatus> VISIBLE = EnumSet.of(PartStatus.NEW, PartStatus.ACCEPTED, PartStatus.CLAIMED, PartStatus.DELAYED,
            PartStatus.READY, PartStatus.PICKED_UP, PartStatus.UNFULFILLED, PartStatus.CANCELLED);

    private static Set<PartStatus> partStatuses(MerchantOrderStatus status) {
        return switch (status) {
            case NEW -> EnumSet.of(PartStatus.NEW);
            case ACCEPTED, PREPARING -> EnumSet.of(PartStatus.ACCEPTED, PartStatus.CLAIMED);
            case DELAYED -> EnumSet.of(PartStatus.DELAYED);
            case READY -> EnumSet.of(PartStatus.READY);
            case PICKED_UP -> EnumSet.of(PartStatus.PICKED_UP);
            case CANCELLED -> EnumSet.of(PartStatus.CANCELLED, PartStatus.UNFULFILLED);
        };
    }

    private static MerchantOrderStatus merchantStatus(PartStatus part) {
        return switch (part) {
            case NEW, AWAITING_PAYMENT, BROADCASTING -> MerchantOrderStatus.NEW;
            case ACCEPTED, CLAIMED -> MerchantOrderStatus.ACCEPTED;
            case DELAYED -> MerchantOrderStatus.DELAYED;
            case READY -> MerchantOrderStatus.READY;
            case PICKED_UP -> MerchantOrderStatus.PICKED_UP;
            case UNFULFILLED, CANCELLED -> MerchantOrderStatus.CANCELLED;
        };
    }

    // ---- menu / items ----

    @Transactional(readOnly = true)
    public PageResponse<CatalogItemResponse> items(UUID merchantId, int page, int size) {
        return PageResponse.of(catalogItems.findByMerchantIdOrderByCategoryAscNameAsc(merchantId,
                PageResponse.request(page, size)), MerchantService::toItemResponse);
    }

    /** The item type follows the store: restaurants list FOOD, Kiranas list GROCERY. */
    @Transactional
    public CatalogItemResponse createItem(UUID merchantId, CatalogItemRequest request) {
        Merchant merchant = load(merchantId);
        CatalogItem item = new CatalogItem();
        item.setMerchantId(merchantId);
        item.setType(merchant.getType() == MerchantType.RESTAURANT ? ItemType.FOOD : ItemType.GROCERY);
        apply(item, request);
        return toItemResponse(catalogItems.save(item));
    }

    @Transactional
    public CatalogItemResponse updateItem(UUID merchantId, UUID itemId, CatalogItemRequest request) {
        CatalogItem item = loadItem(merchantId, itemId);
        apply(item, request);
        return toItemResponse(item);
    }

    @Transactional
    public CatalogItemResponse setAvailability(UUID merchantId, UUID itemId, boolean available) {
        CatalogItem item = loadItem(merchantId, itemId);
        item.setAvailable(available);
        return toItemResponse(item);
    }

    private static void apply(CatalogItem item, CatalogItemRequest request) {
        item.setName(request.name().trim());
        item.setDescription(trimToNull(request.description()));
        item.setPrice(request.price());
        item.setCategory(request.category().trim());
        item.setVeg(request.veg());
        item.setZone(request.zone());
        item.setImageUrl(trimToNull(request.imageUrl()));
        item.setAvailable(request.available());
        item.setBrand(trimToNull(request.brand()));
        item.setPackSize(trimToNull(request.packSize()));
        item.setMrp(request.mrp());
        // TODO(edge-case): reject price > mrp, and FOOD items with grocery-only fields.
    }

    // ----------------------------------------------------------------- helpers

    private static void requireType(Merchant merchant, MerchantType type) {
        if (merchant.getType() != type) {
            throw ApiException.forbidden(type + "_ONLY", "Only " + type.name().toLowerCase() + " stores can do this");
        }
    }

    private CatalogItem loadItem(UUID merchantId, UUID itemId) {
        return catalogItems.findByIdAndMerchantId(itemId, merchantId)
                .orElseThrow(() -> ApiException.notFound("Item"));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    Merchant load(UUID merchantId) {
        return merchants.findById(merchantId)
                .filter(merchant -> merchant.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Merchant"));
    }

    public static MerchantProfileResponse toResponse(Merchant merchant) {
        return new MerchantProfileResponse(merchant.getId(), merchant.getType(), merchant.getStoreName(),
                merchant.getOwnerName(), merchant.getPhone(), merchant.getAddress(),
                GeoUtils.fromPoint(merchant.getLocation()), merchant.isOpen(), merchant.getApprovalStatus(),
                merchant.getRejectionReason(), merchant.getCreatedAt());
    }

    public static CatalogItemResponse toItemResponse(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getType(), item.getName(), item.getDescription(),
                item.getPrice(), item.getCategory(), item.getVeg(), item.getZone(), item.getImageUrl(),
                item.isAvailable(), item.getBrand(), item.getPackSize(), item.getMrp());
    }
}
