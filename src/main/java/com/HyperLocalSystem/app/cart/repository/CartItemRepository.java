package com.HyperLocalSystem.app.cart.repository;

import com.HyperLocalSystem.app.cart.entity.CartItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartIdOrderByCreatedAt(UUID cartId);

    Optional<CartItem> findByCartIdAndItemId(UUID cartId, UUID itemId);

    Optional<CartItem> findByIdAndCartId(UUID id, UUID cartId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem i where i.cartId = :cartId and i.type = :type")
    void deleteByCartIdAndType(@Param("cartId") UUID cartId, @Param("type") ItemType type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem i where i.cartId = :cartId")
    void deleteByCartId(@Param("cartId") UUID cartId);
}
