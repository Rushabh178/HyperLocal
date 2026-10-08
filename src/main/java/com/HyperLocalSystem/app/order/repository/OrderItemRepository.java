package com.HyperLocalSystem.app.order.repository;

import com.HyperLocalSystem.app.catalog.entity.ItemType;
import com.HyperLocalSystem.app.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByOrderIdOrderByCreatedAt(UUID orderId);

    List<OrderItem> findByOrderIdAndTypeOrderByCreatedAt(UUID orderId, ItemType type);

    List<OrderItem> findByOrderIdInOrderByCreatedAt(Collection<UUID> orderIds);
}
