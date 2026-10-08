package com.HyperLocalSystem.app.payment.controller;

import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletResponse;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletTransaction;
import com.HyperLocalSystem.app.payment.service.WalletService;
import com.HyperLocalSystem.app.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponse balance(@AuthenticationPrincipal Jwt jwt) {
        return walletService.balance(CurrentUser.id(jwt));
    }

    @GetMapping("/transactions")
    public PageResponse<WalletTransaction> transactions(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return walletService.transactions(CurrentUser.id(jwt), page, size);
    }
}
