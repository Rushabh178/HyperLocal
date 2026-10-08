package com.HyperLocalSystem.app.order.controller;

import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CancelOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.CheckoutResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderDetail;
import com.HyperLocalSystem.app.order.dto.OrderDtos.OrderSummary;
import com.HyperLocalSystem.app.order.dto.OrderDtos.RateOrderRequest;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TimelineResponse;
import com.HyperLocalSystem.app.order.dto.OrderDtos.TrackingResponse;
import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.service.CustomerOrderService;
import com.HyperLocalSystem.app.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService orderService;

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt,
                                     @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                     @Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(CurrentUser.id(jwt), request, idempotencyKey);
    }

    @GetMapping
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(required = false) OrderStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return orderService.list(CurrentUser.id(jwt), status, page, size);
    }

    @GetMapping("/{orderId}")
    public OrderDetail get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return orderService.get(CurrentUser.id(jwt), orderId);
    }

    @GetMapping("/{orderId}/timeline")
    public TimelineResponse timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return orderService.timeline(CurrentUser.id(jwt), orderId);
    }

    @GetMapping("/{orderId}/tracking")
    public TrackingResponse tracking(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return orderService.tracking(CurrentUser.id(jwt), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDetail cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                              @Valid @RequestBody CancelOrderRequest request) {
        return orderService.cancel(CurrentUser.id(jwt), orderId, request);
    }

    @PostMapping("/{orderId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId,
                     @Valid @RequestBody RateOrderRequest request) {
        orderService.rate(CurrentUser.id(jwt), orderId, request);
    }
}
