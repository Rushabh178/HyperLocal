package com.HyperLocalSystem.app.merchant.repository;

import com.HyperLocalSystem.app.merchant.entity.MerchantDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument, UUID> {

    List<MerchantDocument> findByMerchantIdOrderByType(UUID merchantId);

    Optional<MerchantDocument> findByMerchantIdAndType(UUID merchantId, MerchantDocument.Type type);
}
