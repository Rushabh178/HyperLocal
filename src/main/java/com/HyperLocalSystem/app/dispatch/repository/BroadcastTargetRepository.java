package com.HyperLocalSystem.app.dispatch.repository;

import com.HyperLocalSystem.app.dispatch.entity.BroadcastTarget;
import com.HyperLocalSystem.app.merchant.repository.MerchantRepository;
import com.HyperLocalSystem.app.merchant.repository.NearbyMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BroadcastTargetRepository extends JpaRepository<BroadcastTarget, UUID> {

    Optional<BroadcastTarget> findByOfferIdAndMerchantId(UUID offerId, UUID merchantId);

    /**
     * The nearest open, approved Kiranas in the H3 disk and radius that this order's earlier rounds
     * have not pinged yet.
     */
    @Query(value = """
            select m.merchant_id as "id", m.store_name as "name", m.cuisines as "cuisines",
                   m.rating_sum as "ratingSum", m.rating_count as "ratingCount", m.is_open as "open",
                   m.image_url as "imageUrl", ST_Y(m.location) as "lat", ST_X(m.location) as "lng",
                   ST_Distance(CAST(m.location AS geography), """ + MerchantRepository.POINT + """
            ) as "distanceMeters"
            """ + MerchantRepository.NEARBY_WHERE + """
              and m.is_open
              and m.merchant_id not in (
                  select t.merchant_id from broadcast_targets t
                  join broadcast_offers o on o.offer_id = t.offer_id
                  where o.order_id = :orderId)
            order by "distanceMeters"
            limit :limit
            """, nativeQuery = true)
    List<NearbyMerchant> findNextKiranas(@Param("cells") Collection<String> cells, @Param("type") String type,
                                         @Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius,
                                         @Param("orderId") UUID orderId, @Param("limit") int limit);
}
