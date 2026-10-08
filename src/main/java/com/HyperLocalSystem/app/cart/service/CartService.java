package com.HyperLocalSystem.app.cart.service;

import com.HyperLocalSystem.app.cart.dto.CartDtos.AddCartItemRequest;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartNudge;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartResponse;
import com.HyperLocalSystem.app.cart.dto.CartDtos.UpdateCartItemRequest;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * TODO: persist the cart (table carts + cart_items, or Redis keyed by customer_id) and price it.
 * Bill rules: one courier → one delivery fee + one flat convenience fee; no grocery minimum.
 */
@Service
public class CartService {

    public CartResponse get(UUID customerId) {
        throw new NotImplementedException("Get cart");
    }

    public CartResponse addItem(UUID customerId, AddCartItemRequest request) {
        throw new NotImplementedException("Add cart item");
    }

    public CartResponse updateItem(UUID customerId, UUID cartItemId, UpdateCartItemRequest request) {
        throw new NotImplementedException("Update cart item");
    }

    public CartResponse removeItem(UUID customerId, UUID cartItemId) {
        throw new NotImplementedException("Remove cart item");
    }

    public void clear(UUID customerId) {
        throw new NotImplementedException("Clear cart");
    }

    /** TODO: verify the address belongs to the customer and is inside the 2 km radius of the cart's merchants. */
    public CartResponse selectAddress(UUID customerId, UUID addressId) {
        throw new NotImplementedException("Select delivery address");
    }

    /** TODO: if grocery subtotal < ops.groceryCartNudgeThreshold, suggest high-margin meals nearby. */
    public CartNudge nudge(UUID customerId) {
        throw new NotImplementedException("Cart nudge");
    }
}
