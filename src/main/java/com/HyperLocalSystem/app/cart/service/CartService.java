package com.HyperLocalSystem.app.cart.service;

import com.HyperLocalSystem.app.cart.dto.CartDtos.AddCartItemRequest;
import com.HyperLocalSystem.app.cart.dto.CartDtos.BillSummary;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartItemResponse;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartItemType;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartNudge;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartResponse;
import com.HyperLocalSystem.app.cart.dto.CartDtos.SuggestedMeal;
import com.HyperLocalSystem.app.cart.dto.CartDtos.UpdateCartItemRequest;
import com.HyperLocalSystem.app.cart.entity.Cart;
import com.HyperLocalSystem.app.cart.entity.CartItem;
import com.HyperLocalSystem.app.cart.repository.CartItemRepository;
import com.HyperLocalSystem.app.cart.repository.CartRepository;
import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.catalog.repository.CatalogItemRepository;
import com.HyperLocalSystem.app.catalog.service.CatalogService;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.common.geo.GeoUtils;
import com.HyperLocalSystem.app.common.model.AccountStatus;
import com.HyperLocalSystem.app.config.AppProperties;
import com.HyperLocalSystem.app.customer.entity.CustomerAddress;
import com.HyperLocalSystem.app.customer.repository.CustomerAddressRepository;
import com.HyperLocalSystem.app.delivery.DeliveryPricing;
import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import com.HyperLocalSystem.app.merchant.entity.MerchantType;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One persistent cart per customer holding food (one restaurant) and groceries together.
 * Lines store only item + quantity; prices always come from the live catalogue.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    static final int MAX_QUANTITY = 20;

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CatalogItemRepository catalogItems;
    private final MerchantRepository merchants;
    private final CustomerAddressRepository addresses;
    private final CatalogService catalogService;
    private final BillCalculator billCalculator;
    private final DeliveryPricing pricing;
    private final AppProperties properties;

    /** A priced snapshot of the cart, also used by checkout. */
    /** {@code feeKm}: the distance the delivery fee was priced on, null when unknown. */
    public record PricedCart(Cart cart, List<PricedLine> food, List<PricedLine> groceries, BillSummary bill, Double feeKm) {

        public boolean isEmpty() {
            return food.isEmpty() && groceries.isEmpty();
        }
    }

    public record PricedLine(CartItem line, CatalogItem item) {

        public BigDecimal lineTotal() {
            return item.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
        }
    }

    @Transactional(readOnly = true)
    public CartResponse get(UUID customerId) {
        return carts.findByCustomerId(customerId).map(cart -> toResponse(customerId, priceAt(customerId, cart)))
                .orElseGet(() -> toResponse(customerId, null));
    }

    /** Food from a different restaurant replaces the food section; the same item again bumps the quantity. */
    @Transactional
    public CartResponse addItem(UUID customerId, AddCartItemRequest request) {
        CatalogItem item = catalogItems.findById(request.itemId())
                .filter(CatalogItem::isAvailable)
                .orElseThrow(() -> ApiException.notFound("Item"));
        if (!item.getType().name().equals(request.itemType().name())) {
            throw ApiException.badRequest("ITEM_TYPE_MISMATCH", "This item is not a " + request.itemType() + " item");
        }
        Merchant store = merchants.findById(item.getMerchantId())
                .filter(m -> m.getApprovalStatus() == ApprovalStatus.APPROVED && m.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Item"));
        if (!store.isOpen()) {
            throw ApiException.conflict("STORE_CLOSED", store.getStoreName() + " is closed right now");
        }

        Cart cart = carts.findByCustomerId(customerId).orElseGet(() -> newCart(customerId));
        if (item.getType() == ItemType.FOOD) {
            if (!item.getMerchantId().equals(request.restaurantId())) {
                throw ApiException.badRequest("RESTAURANT_MISMATCH", "restaurantId must be the restaurant serving this item");
            }
            if (cart.getRestaurantId() != null && !cart.getRestaurantId().equals(item.getMerchantId())) {
                cartItems.deleteByCartIdAndType(cart.getId(), ItemType.FOOD);
            }
            cart.setRestaurantId(item.getMerchantId());
        }

        CartItem line = cartItems.findByCartIdAndItemId(cart.getId(), item.getId()).orElseGet(() -> {
            CartItem fresh = new CartItem();
            fresh.setCartId(cart.getId());
            fresh.setItemId(item.getId());
            fresh.setType(item.getType());
            return fresh;
        });
        line.setQuantity(Math.min(line.getQuantity() + request.quantity(), MAX_QUANTITY));
        cartItems.save(line);
        carts.save(cart);
        return toResponse(customerId, priceAt(customerId, cart));
    }

    /** Quantity 0 removes the line. */
    @Transactional
    public CartResponse updateItem(UUID customerId, UUID cartItemId, UpdateCartItemRequest request) {
        Cart cart = loadCart(customerId);
        CartItem line = cartItems.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        if (request.quantity() == 0) {
            return removeItem(customerId, cartItemId);
        }
        line.setQuantity(request.quantity());
        return toResponse(customerId, priceAt(customerId, cart));
    }

    @Transactional
    public CartResponse removeItem(UUID customerId, UUID cartItemId) {
        Cart cart = loadCart(customerId);
        CartItem line = cartItems.findByIdAndCartId(cartItemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item"));
        cartItems.delete(line);
        cartItems.flush();
        PricedCart priced = priceAt(customerId, cart);
        if (priced.food().isEmpty()) {
            cart.setRestaurantId(null);
        }
        return toResponse(customerId, priced);
    }

    @Transactional
    public void clear(UUID customerId) {
        carts.findByCustomerId(customerId).ifPresent(cart -> {
            cartItems.deleteByCartId(cart.getId());
            carts.delete(cart);
        });
    }

    /** The address must be the customer's and in range of the restaurant (6 km) and of an open Kirana (2 km). */
    @Transactional
    public CartResponse selectAddress(UUID customerId, UUID addressId) {
        Cart cart = carts.findByCustomerId(customerId).orElseGet(() -> newCart(customerId));
        CustomerAddress address = addresses.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)
                .orElseThrow(() -> ApiException.notFound("Address"));
        PricedCart priced = price(cart, address);
        ensureServiceable(priced, address);
        cart.setDeliveryAddressId(address.getId());
        return toResponse(customerId, priced);
    }

    @Transactional(readOnly = true)
    public CartNudge nudge(UUID customerId) {
        Optional<Cart> cart = carts.findByCustomerId(customerId);
        CartNudge nudge = cart.map(c -> buildNudge(customerId, priceAt(customerId, c))).orElse(null);
        return nudge != null ? nudge
                : new CartNudge(BigDecimal.ZERO, properties.ops().groceryCartNudgeThreshold(), null, List.of());
    }

    // ----------------------------------------------------------------- used by checkout

    /** Locks the cart row for the rest of the checkout transaction, then prices it for delivery to {@code address}. */
    @Transactional
    public PricedCart lockAndPrice(UUID customerId, CustomerAddress address) {
        return price(carts.lockByCustomerId(customerId)
                .orElseThrow(() -> ApiException.badRequest("CART_EMPTY", "Your cart is empty")), address);
    }

    /**
     * Straight-line range check per part, saying which part can't come: the restaurant must be within
     * {@code app.delivery.restaurant-radius-km} (6 km) and an open Kirana within {@code kirana-radius-km} (2 km).
     */
    public void ensureServiceable(PricedCart priced, CustomerAddress address) {
        boolean hasFood = priced.cart().getRestaurantId() != null && !priced.food().isEmpty();
        if (hasFood) {
            int radius = pricing.radiusMeters(MerchantType.RESTAURANT);
            Merchant restaurant = merchants.findById(priced.cart().getRestaurantId())
                    .orElseThrow(() -> ApiException.notFound("Restaurant"));
            double meters = GeoUtils.haversineMeters(GeoUtils.fromPoint(restaurant.getLocation()),
                    GeoUtils.fromPoint(address.getLocation()));
            if (meters > radius) {
                throw ApiException.badRequest("ADDRESS_NOT_SERVICEABLE", "Food from " + restaurant.getStoreName()
                        + " can't be delivered to this address: it's more than " + km(radius) + " km away"
                        + (priced.groceries().isEmpty() ? "." : ". Your groceries can."));
            }
        }
        if (!priced.groceries().isEmpty() && catalogService.openNearby(MerchantType.KIRANA,
                address.getLocation().getY(), address.getLocation().getX()).isEmpty()) {
            throw ApiException.badRequest("ADDRESS_NOT_SERVICEABLE", "Groceries can't be delivered to this address: no grocery store is open within "
                    + km(pricing.radiusMeters(MerchantType.KIRANA)) + " km" + (hasFood ? ". Your food can." : "."));
        }
    }

    private static String km(int meters) {
        return meters % 1000 == 0 ? Integer.toString(meters / 1000) : Double.toString(meters / 1000.0);
    }

    // ----------------------------------------------------------------- helpers

    private Cart newCart(UUID customerId) {
        Cart cart = new Cart();
        cart.setCustomerId(customerId);
        // TODO(edge-case): two first adds at the same moment race on uq customer_id → one gets 409; the app can retry.
        return carts.saveAndFlush(cart);
    }

    private Cart loadCart(UUID customerId) {
        return carts.findByCustomerId(customerId).orElseThrow(() -> ApiException.notFound("Cart"));
    }

    /** Prices the cart for its delivery pin (the chosen address, else the default one), if there is one. */
    private PricedCart priceAt(UUID customerId, Cart cart) {
        return price(cart, deliveryPin(customerId, cart).orElse(null));
    }

    /**
     * Prices the cart. The delivery fee depends on the fee distance of the farthest pickup: the restaurant.
     * Groceries come from a Kirana within {@code kirana-radius-km}, which is inside the free-delivery zone.
     * TODO(edge-case): if kirana-radius-km is ever raised above free-delivery-km, price the grocery pickup too.
     */
    private PricedCart price(Cart cart, CustomerAddress pin) {
        List<CartItem> lines = cartItems.findByCartIdOrderByCreatedAt(cart.getId());
        Map<UUID, CatalogItem> byId = catalogItems.findAllById(lines.stream().map(CartItem::getItemId).toList())
                .stream().collect(Collectors.toMap(CatalogItem::getId, Function.identity()));
        // TODO(edge-case): surface lines whose item became unavailable instead of silently pricing them.
        List<PricedLine> priced = lines.stream()
                .filter(line -> byId.containsKey(line.getItemId()))
                .map(line -> new PricedLine(line, byId.get(line.getItemId())))
                .toList();
        List<PricedLine> food = priced.stream().filter(p -> p.line().getType() == ItemType.FOOD).toList();
        List<PricedLine> groceries = priced.stream().filter(p -> p.line().getType() == ItemType.GROCERY).toList();
        Double feeKm = null;
        if (!food.isEmpty() && cart.getRestaurantId() != null && pin != null) {
            feeKm = merchants.findById(cart.getRestaurantId())
                    .map(restaurant -> pricing.feeKm(GeoUtils.fromPoint(restaurant.getLocation()), GeoUtils.fromPoint(pin.getLocation())))
                    .orElse(null);
        }
        return new PricedCart(cart, food, groceries, billCalculator.bill(sum(food), sum(groceries), feeKm), feeKm);
    }

    private static BigDecimal sum(List<PricedLine> lines) {
        return lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Grocery-only cart under ₹150: suggest the priciest dishes from open restaurants within
     * {@code meal-nudge-radius-km} (2 km) of the delivery pin, so adding one keeps delivery free.
     */
    private CartNudge buildNudge(UUID customerId, PricedCart priced) {
        BigDecimal threshold = properties.ops().groceryCartNudgeThreshold();
        BigDecimal groceryTotal = priced.bill().grocerySubtotal();
        if (!priced.food().isEmpty() || groceryTotal.signum() == 0 || groceryTotal.compareTo(threshold) >= 0) {
            return null;
        }
        List<SuggestedMeal> meals = deliveryPin(customerId, priced.cart())
                .map(address -> catalogService.openNearby(MerchantType.RESTAURANT,
                        address.getLocation().getY(), address.getLocation().getX(), pricing.mealNudgeRadiusMeters()))
                .filter(ids -> !ids.isEmpty())
                .map(ids -> catalogItems.findTop5ByMerchantIdInAndTypeAndAvailableTrueOrderByPriceDesc(ids, ItemType.FOOD)
                        .stream().map(this::toSuggestedMeal).toList())
                .orElse(List.of());
        return new CartNudge(groceryTotal, threshold,
                "Add a meal from a restaurant nearby and get free delivery", meals);
    }

    private SuggestedMeal toSuggestedMeal(CatalogItem item) {
        String restaurant = merchants.findById(item.getMerchantId()).map(Merchant::getStoreName).orElse(null);
        return new SuggestedMeal(item.getId(), item.getMerchantId(), restaurant, item.getName(), item.getDescription(),
                item.getPrice(), Boolean.TRUE.equals(item.getVeg()), item.getImageUrl());
    }

    /** The cart's chosen address, else the customer's default one. */
    private Optional<CustomerAddress> deliveryPin(UUID customerId, Cart cart) {
        if (cart.getDeliveryAddressId() != null) {
            return addresses.findByIdAndCustomerIdAndDeletedFalse(cart.getDeliveryAddressId(), customerId);
        }
        return addresses.findByCustomerIdAndDeletedFalseOrderByDefaultAddressDescUpdatedAtDesc(customerId).stream().findFirst();
    }

    private CartResponse toResponse(UUID customerId, PricedCart priced) {
        if (priced == null) {
            return new CartResponse(null, List.of(), List.of(), null, billCalculator.bill(BigDecimal.ZERO, BigDecimal.ZERO, null), null,
                    null, pricing.freeDeliveryKm());
        }
        return new CartResponse(priced.cart().getRestaurantId(), priced.food().stream().map(CartService::toLine).toList(),
                priced.groceries().stream().map(CartService::toLine).toList(), priced.cart().getDeliveryAddressId(),
                priced.bill(), buildNudge(customerId, priced), priced.feeKm(), pricing.freeDeliveryKm());
    }

    private static CartItemResponse toLine(PricedLine p) {
        return new CartItemResponse(p.line().getId(), CartItemType.valueOf(p.line().getType().name()), p.item().getId(),
                p.item().getName(), p.item().getPrice(), p.line().getQuantity(), BillCalculator.money(p.lineTotal()),
                p.item().getZone());
    }
}
