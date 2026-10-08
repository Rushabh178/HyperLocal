package com.HyperLocalSystem.app.merchant.repository;

import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByPhone(String phone);

    Page<Merchant> findByApprovalStatusOrderByCreatedAt(ApprovalStatus status, Pageable pageable);

    /** Atomic so two ratings submitted at once can't lose an update. */
    @Modifying
    @Query("update Merchant m set m.ratingSum = m.ratingSum + :stars, m.ratingCount = m.ratingCount + 1 where m.id = :id")
    void addRating(@Param("id") UUID id, @Param("stars") int stars);
}
