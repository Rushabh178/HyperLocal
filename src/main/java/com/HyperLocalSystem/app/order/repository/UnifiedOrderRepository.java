package com.HyperLocalSystem.app.order.repository;

import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnifiedOrderRepository extends JpaRepository<UnifiedOrder, UUID> {

    Optional<UnifiedOrder> findByIdAndCustomerId(UUID id, UUID customerId);
}
