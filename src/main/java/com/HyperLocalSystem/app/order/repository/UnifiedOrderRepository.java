package com.HyperLocalSystem.app.order.repository;

import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface UnifiedOrderRepository extends JpaRepository<UnifiedOrder, UUID> {

    Optional<UnifiedOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<UnifiedOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<UnifiedOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<UnifiedOrder> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, OrderStatus status, Pageable pageable);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<OrderStatus> statuses);
}
