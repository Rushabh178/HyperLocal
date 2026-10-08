package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.NotImplementedException;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletResponse;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletTransaction;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** TODO: wallet_transactions ledger table; balance = SUM(credits) - SUM(debits). */
@Service
public class WalletService {

    public WalletResponse balance(UUID customerId) {
        throw new NotImplementedException("Wallet balance");
    }

    public PageResponse<WalletTransaction> transactions(UUID customerId, int page, int size) {
        throw new NotImplementedException("Wallet transactions");
    }
}
