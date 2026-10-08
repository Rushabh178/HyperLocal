package com.HyperLocalSystem.app.payment.service;

import com.HyperLocalSystem.app.common.api.PageResponse;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.customer.repository.CustomerRepository;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletResponse;
import com.HyperLocalSystem.app.payment.dto.PaymentDtos.WalletTransaction;
import com.HyperLocalSystem.app.payment.entity.WalletEntry;
import com.HyperLocalSystem.app.payment.entity.WalletTransactionType;
import com.HyperLocalSystem.app.payment.repository.WalletEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * In-app wallet as an append-only ledger (wallet_entries): balance = SUM(credits) - SUM(debits).
 * Entries are never updated or deleted; a correction is a new entry.
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletEntryRepository entries;
    private final CustomerRepository customers;

    @Transactional(readOnly = true)
    public WalletResponse balance(UUID customerId) {
        return new WalletResponse(entries.balanceOf(customerId), "INR");
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransaction> transactions(UUID customerId, int page, int size) {
        return PageResponse.of(entries.findByCustomerIdOrderByCreatedAtDesc(customerId, PageResponse.request(page, size)),
                e -> new WalletTransaction(e.getId(), e.getType(), e.getAmount(), e.getReason(), e.getOrderId(), e.getCreatedAt()));
    }

    /** Idempotent on {@code reference}: crediting the same refund twice is a no-op. */
    @Transactional
    public void credit(UUID customerId, BigDecimal amount, String reason, UUID orderId, String reference) {
        if (amount.signum() <= 0 || entries.existsByReference(reference)) {
            return;
        }
        entries.save(new WalletEntry(customerId, WalletTransactionType.CREDIT, amount, reason, orderId, reference));
    }

    /**
     * Locks the customer row first, so two payments at the same moment can't both pass the balance
     * check and overdraw the wallet.
     */
    @Transactional
    public void debit(UUID customerId, BigDecimal amount, String reason, UUID orderId, String reference) {
        customers.lockById(customerId).orElseThrow(() -> ApiException.notFound("Customer"));
        if (entries.balanceOf(customerId).compareTo(amount) < 0) {
            throw ApiException.conflict("INSUFFICIENT_WALLET_BALANCE", "Your wallet balance is too low for this order");
        }
        entries.save(new WalletEntry(customerId, WalletTransactionType.DEBIT, amount, reason, orderId, reference));
    }
}
