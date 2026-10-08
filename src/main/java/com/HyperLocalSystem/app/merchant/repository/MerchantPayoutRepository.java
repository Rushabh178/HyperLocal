package com.HyperLocalSystem.app.merchant.repository;

import com.HyperLocalSystem.app.merchant.entity.MerchantPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MerchantPayoutRepository extends JpaRepository<MerchantPayout, UUID> {

    Page<MerchantPayout> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);
}
