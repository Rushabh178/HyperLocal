package com.HyperLocalSystem.app.agent.repository;

import com.HyperLocalSystem.app.agent.entity.DeliveryAgent;
import com.HyperLocalSystem.app.agent.entity.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryAgentRepository extends JpaRepository<DeliveryAgent, UUID> {

    Optional<DeliveryAgent> findByPhone(String phone);

    Optional<DeliveryAgent> findByEmailIgnoreCase(String email);

    Page<DeliveryAgent> findByVerificationStatusOrderByCreatedAt(VerificationStatus status, Pageable pageable);

    /**
     * Nearest online, approved, free riders around a pickup: H3 cells first, then exact distance.
     * Skips riders already offered this order and riders holding another open offer.
     */
    @Query(value = """
            select a.agent_id from delivery_agents a
            where a.h3_index in (:cells)
              and a.status = 'AVAILABLE' and a.verification_status = 'APPROVED' and a.account_status = 'ACTIVE'
              and a.current_location is not null
              and ST_DWithin(CAST(a.current_location AS geography),
                             CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), :radius)
              and a.agent_id not in (select t.agent_id from task_offers t where t.order_id = :orderId)
              and not exists (select 1 from task_offers o where o.agent_id = a.agent_id and o.status = 'OFFERED')
            order by ST_Distance(CAST(a.current_location AS geography),
                                 CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography))
            limit :limit
            """, nativeQuery = true)
    List<UUID> findNearestAvailable(@Param("cells") Collection<String> cells, @Param("lat") double lat,
                                    @Param("lng") double lng, @Param("radius") int radius,
                                    @Param("orderId") UUID orderId, @Param("limit") int limit);

    /** AVAILABLE → ACTIVE only if still AVAILABLE: a rider can't take two first trips at once. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update delivery_agents set status = 'ACTIVE' where agent_id = :agentId and status = 'AVAILABLE'", nativeQuery = true)
    int markActive(@Param("agentId") UUID agentId);

    /** Riders on a trip near a point: candidates for a batching injection. */
    @Query(value = """
            select a.agent_id from delivery_agents a
            where a.h3_index in (:cells) and a.status = 'ACTIVE' and a.account_status = 'ACTIVE'
            """, nativeQuery = true)
    List<UUID> findActiveIn(@Param("cells") Collection<String> cells);
}
