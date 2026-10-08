package com.HyperLocalSystem.app.payment.controller;

import com.HyperLocalSystem.app.payment.dto.PaymentDtos.PaymentHoldResponse;
import com.HyperLocalSystem.app.payment.service.PaymentService;
import com.HyperLocalSystem.app.security.CurrentUser;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Retry / re-create the UPI hold for an order (checkout already creates the first one). */
    @PostMapping("/orders/{orderId}/upi-hold")
    public PaymentHoldResponse createHold(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return paymentService.createHold(CurrentUser.id(jwt), orderId);
    }

    @GetMapping("/orders/{orderId}")
    public PaymentHoldResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return paymentService.status(CurrentUser.id(jwt), orderId);
    }

    /** Public: called by the payment gateway, authenticated by its signature header. */
    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public void webhook(@RequestHeader(name = "X-Signature", required = false) String signature,
                        @RequestBody String rawBody) {
        paymentService.handleWebhook(signature, rawBody);
    }
}
