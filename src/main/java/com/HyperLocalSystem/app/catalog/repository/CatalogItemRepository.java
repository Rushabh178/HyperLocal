package com.HyperLocalSystem.app.catalog.repository;

import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    Page<CatalogItem> findByMerchantIdOrderByCategoryAscNameAsc(UUID merchantId, Pageable pageable);

    List<CatalogItem> findByMerchantIdOrderByCategoryAscNameAsc(UUID merchantId);

    Optional<CatalogItem> findByIdAndMerchantId(UUID id, UUID merchantId);
}
