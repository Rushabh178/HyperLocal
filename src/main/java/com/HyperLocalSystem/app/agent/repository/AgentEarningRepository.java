package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.AgentEarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgentEarningRepository extends JpaRepository<AgentEarning, UUID> {

    boolean existsByAgentIdAndOrderIdAndType(UUID agentId, UUID orderId, AgentEarning.Type type);

    /** {@code [type, count, sum]} per earning type within [from, to). */
    @Query("""
            select e.type, count(e), coalesce(sum(e.amount), 0) from AgentEarning e
            where e.agentId = :agentId and e.createdAt >= :from and e.createdAt < :to
            group by e.type
            """)
    List<Object[]> totalsByType(@Param("agentId") UUID agentId, @Param("from") Instant from, @Param("to") Instant to);

    /** {@code [orderId, sum]} for a page of delivered orders. */
    @Query("""
            select e.orderId, sum(e.amount) from AgentEarning e
            where e.agentId = :agentId and e.orderId in :orderIds
            group by e.orderId
            """)
    List<Object[]> totalsByOrder(@Param("agentId") UUID agentId, @Param("orderIds") Collection<UUID> orderIds);
}
