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

    /** Offers past their deadline, locked so only one instance expires (and announces) each of them. */
    @Query(value = """
            select * from task_offers where status = 'OFFERED' and expires_at <= :now
            limit :limit for update skip locked
            """, nativeQuery = true)
    List<TaskOffer> lockExpired(@Param("now") Instant now, @Param("limit") int limit);

    List<TaskOffer> findByOrderIdAndStatus(UUID orderId, TaskOffer.Status status);

    List<TaskOffer> findByAgentIdAndStatusAndExpiresAtAfterOrderByCreatedAt(UUID agentId, TaskOffer.Status status, Instant now);

    /** Moves the order's open offer (at most one) to CANCELLED and returns it so the rider can be told. */
    default List<TaskOffer> withdrawOpen(UUID orderId) {
        List<TaskOffer> open = findByOrderIdAndStatus(orderId, TaskOffer.Status.OFFERED);
        open.forEach(offer -> offer.setStatus(TaskOffer.Status.CANCELLED));
        return open;
    }
}
