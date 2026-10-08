package com.HyperLocalSystem.app.payment.repository;

import com.HyperLocalSystem.app.payment.entity.MerchantSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantSettlementRepository extends JpaRepository<MerchantSettlement, UUID> {

    boolean existsByMerchantIdAndOrderId(UUID merchantId, UUID orderId);

    Optional<MerchantSettlement> findByMerchantIdAndOrderId(UUID merchantId, UUID orderId);
}
