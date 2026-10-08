package com.HyperLocalSystem.app.cart.controller;

import com.HyperLocalSystem.app.cart.dto.CartDtos.AddCartItemRequest;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartNudge;
import com.HyperLocalSystem.app.cart.dto.CartDtos.CartResponse;
import com.HyperLocalSystem.app.cart.dto.CartDtos.SelectAddressRequest;
import com.HyperLocalSystem.app.cart.dto.CartDtos.UpdateCartItemRequest;
import com.HyperLocalSystem.app.cart.service.CartService;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cartService.get(CurrentUser.id(jwt));
    }

    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(CurrentUser.id(jwt), request);
    }

    @PatchMapping("/items/{cartItemId}")
    public CartResponse updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID cartItemId,
                                   @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(CurrentUser.id(jwt), cartItemId, request);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID cartItemId) {
        return cartService.removeItem(CurrentUser.id(jwt), cartItemId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        cartService.clear(CurrentUser.id(jwt));
    }

    @PutMapping("/address")
    public CartResponse selectAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SelectAddressRequest request) {
        return cartService.selectAddress(CurrentUser.id(jwt), request.addressId());
    }

    @GetMapping("/nudges")
    public CartNudge nudge(@AuthenticationPrincipal Jwt jwt) {
        return cartService.nudge(CurrentUser.id(jwt));
    }
}
