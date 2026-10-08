package com.HyperLocalSystem.app.order.repository;

import com.HyperLocalSystem.app.order.entity.OrderStatus;
import com.HyperLocalSystem.app.order.entity.PartStatus;
import com.HyperLocalSystem.app.order.entity.UnifiedOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnifiedOrderRepository extends JpaRepository<UnifiedOrder, UUID> {

    Optional<UnifiedOrder> findByIdAndCustomerId(UUID id, UUID customerId);

    Optional<UnifiedOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<UnifiedOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<UnifiedOrder> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, OrderStatus status, Pageable pageable);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, Collection<OrderStatus> statuses);

    Optional<UnifiedOrder> findByIdAndRestaurantId(UUID id, UUID restaurantId);

    Optional<UnifiedOrder> findByIdAndKiranaId(UUID id, UUID kiranaId);

    Page<UnifiedOrder> findByRestaurantIdAndFoodStatusInOrderByCreatedAtDesc(
            UUID restaurantId, Collection<PartStatus> statuses, Pageable pageable);

    Page<UnifiedOrder> findByKiranaIdAndGroceryStatusInOrderByCreatedAtDesc(
            UUID kiranaId, Collection<PartStatus> statuses, Pageable pageable);

    Page<UnifiedOrder> findByDeliveryAgentIdAndStatusOrderByCompletedAtDesc(UUID agentId, OrderStatus status, Pageable pageable);

    /**
     * Confirmed orders that still need a rider: food orders right away, grocery-only ones once a
     * Kirana has claimed them, and only while no offer is pending.
     */
    String AWAITING_RIDER = """
            o.status = 'CONFIRMED' and o.delivery_agent_id is null
              and (o.food_status is not null or o.grocery_status in ('CLAIMED', 'READY'))
              and not exists (select 1 from task_offers t where t.order_id = o.order_id and t.status = 'OFFERED')
            """;

    @Query(value = "select o.order_id from unified_orders o where " + AWAITING_RIDER + " order by o.created_at limit :limit",
            nativeQuery = true)
    List<UUID> findIdsAwaitingRider(@Param("limit") int limit);

    /** Locks one order for dispatch; empty if another instance holds it or it no longer needs a rider. */
    @Query(value = "select * from unified_orders o where o.order_id = :orderId and " + AWAITING_RIDER + " for update skip locked",
            nativeQuery = true)
    Optional<UnifiedOrder> lockIfAwaitingRider(@Param("orderId") UUID orderId);
}
