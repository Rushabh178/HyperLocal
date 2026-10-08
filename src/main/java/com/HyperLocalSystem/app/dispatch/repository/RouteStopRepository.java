package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

    List<RouteStop> findByRouteIdOrderBySequence(UUID routeId);

    List<RouteStop> findByOrderIdOrderBySequence(UUID orderId);

    List<RouteStop> findByOrderIdIn(Collection<UUID> orderIds);

    Optional<RouteStop> findByIdAndAgentId(UUID id, UUID agentId);

    Optional<RouteStop> findFirstByOrderIdAndMerchantId(UUID orderId, UUID merchantId);

    /** Orders the rider still has unfinished stops for: the ones whose customers are tracking them. */
    @Query("""
            select distinct s.orderId from RouteStop s
            where s.agentId = :agentId
              and s.state in (com.HyperLocalSystem.app.common.model.StopState.PENDING,
                              com.HyperLocalSystem.app.common.model.StopState.EN_ROUTE,
                              com.HyperLocalSystem.app.common.model.StopState.ARRIVED)
            """)
    List<UUID> findActiveOrderIds(@Param("agentId") UUID agentId);
}
