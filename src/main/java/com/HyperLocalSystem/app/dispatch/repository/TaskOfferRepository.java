package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.TaskOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskOfferRepository extends JpaRepository<TaskOffer, UUID> {

    /** First accept wins: only an OFFERED, unexpired offer of this rider moves to ACCEPTED. 1 = won. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update task_offers set status = 'ACCEPTED', responded_at = :now
            where offer_id = :offerId and agent_id = :agentId and status = 'OFFERED' and expires_at > :now
            """, nativeQuery = true)
    int accept(@Param("offerId") UUID offerId, @Param("agentId") UUID agentId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update task_offers set status = 'REJECTED', responded_at = :now, reject_reason = :reason
            where offer_id = :offerId and agent_id = :agentId and status = 'OFFERED'
            """, nativeQuery = true)
    int reject(@Param("offerId") UUID offerId, @Param("agentId") UUID agentId, @Param("reason") String reason,
               @Param("now") Instant now);

    /** Bulk timeout; safe to run on every instance at once. */
    @Modifying
    @Query(value = "update task_offers set status = 'EXPIRED' where status = 'OFFERED' and expires_at <= :now", nativeQuery = true)
    int expireDue(@Param("now") Instant now);

    @Modifying
    @Query(value = "update task_offers set status = 'CANCELLED' where order_id = :orderId and status = 'OFFERED'", nativeQuery = true)
    int cancelOpenForOrder(@Param("orderId") UUID orderId);

    List<TaskOffer> findByAgentIdAndStatusAndExpiresAtAfterOrderByCreatedAt(UUID agentId, TaskOffer.Status status, Instant now);
}
