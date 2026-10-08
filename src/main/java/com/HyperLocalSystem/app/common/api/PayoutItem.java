package com.HyperLocalSystem.app.common.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A bank transfer of earnings (partner or store). {@code status}: PENDING, PAID or FAILED. */
public record PayoutItem(UUID payoutId, BigDecimal amount, String status, String utr, Instant paidAt) {
}
