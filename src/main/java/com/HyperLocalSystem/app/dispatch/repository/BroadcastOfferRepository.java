package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.BroadcastOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BroadcastOfferRepository extends JpaRepository<BroadcastOffer, UUID> {

    /**
     * First tap wins: only one caller can move an OPEN, unexpired round to CLAIMED, and only a
     * Kirana that round was sent to. Returns the number of rows changed (1 = you won).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update broadcast_offers set status = 'CLAIMED', claimed_by = :merchantId, claimed_at = :now
            where offer_id = :offerId and status = 'OPEN' and expires_at > :now
              and exists (select 1 from broadcast_targets t where t.offer_id = :offerId and t.merchant_id = :merchantId)
            """, nativeQuery = true)
    int claim(@Param("offerId") UUID offerId, @Param("merchantId") UUID merchantId, @Param("now") Instant now);

    /** Open, unexpired rounds that were sent to this Kirana (a handful at most, so not paged). */
    @Query(value = """
            select o.* from broadcast_offers o
            join broadcast_targets t on t.offer_id = o.offer_id
            where t.merchant_id = :merchantId and o.status = 'OPEN' and o.expires_at > :now
            order by o.expires_at
            """, nativeQuery = true)
    List<BroadcastOffer> findOpenFor(@Param("merchantId") UUID merchantId, @Param("now") Instant now);

    List<BroadcastOffer> findByOrderIdOrderByRoundDesc(UUID orderId);

    /** Rounds whose time has come: SCHEDULED to open, OPEN to expire. SKIP LOCKED lets several instances share the work. */
    @Query(value = """
            select * from broadcast_offers
            where (status = 'SCHEDULED' and starts_at <= :now) or (status = 'OPEN' and expires_at <= :now)
            order by starts_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<BroadcastOffer> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
