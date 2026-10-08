package com.HyperLocalSystem.app.order.repository;

import com.HyperLocalSystem.app.order.entity.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRatingRepository extends JpaRepository<OrderRating, UUID> {
}
