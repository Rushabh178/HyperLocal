package com.HyperLocalSystem.app.merchant.repository;

import com.HyperLocalSystem.app.merchant.entity.ApprovalStatus;
import com.HyperLocalSystem.app.merchant.entity.Merchant;
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

/**
 * Nearby searches always narrow by {@code h3_index IN (:cells)} first (the H3 disk around the point,
 * see SpatialIndexService.cellsWithin) and only then apply the exact PostGIS distance, so they
 * never scan the whole table. {@code :type} is the MerchantType name.
 */
public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    String POINT = "CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)";

    String NEARBY_WHERE = """
            from merchants m
            where m.h3_index in (:cells)
              and m.merchant_type = :type
              and m.approval_status = 'APPROVED'
              and m.account_status = 'ACTIVE'
              and ST_DWithin(CAST(m.location AS geography), """ + POINT + """
            , :radius)
            """;

    Optional<Merchant> findByPhone(String phone);

    Page<Merchant> findByApprovalStatusOrderByCreatedAt(ApprovalStatus status, Pageable pageable);

    /** Open stores first, then nearest. {@code namePattern} / {@code cuisinePattern} are ILIKE patterns or null. */
    @Query(value = """
            select m.merchant_id as "id", m.store_name as "name", m.cuisines as "cuisines",
                   m.rating_sum as "ratingSum", m.rating_count as "ratingCount", m.is_open as "open",
                   m.image_url as "imageUrl", ST_Y(m.location) as "lat", ST_X(m.location) as "lng",
                   ST_Distance(CAST(m.location AS geography), """ + POINT + """
            ) as "distanceMeters"
            """ + NEARBY_WHERE + """
              and (CAST(:cuisinePattern AS text) is null or m.cuisines ilike :cuisinePattern)
              and (CAST(:namePattern AS text) is null or m.store_name ilike :namePattern or m.cuisines ilike :namePattern)
            order by m.is_open desc, "distanceMeters"
            """, countQuery = """
            select count(*)
            """ + NEARBY_WHERE + """
              and (CAST(:cuisinePattern AS text) is null or m.cuisines ilike :cuisinePattern)
              and (CAST(:namePattern AS text) is null or m.store_name ilike :namePattern or m.cuisines ilike :namePattern)
            """, nativeQuery = true)
    Page<NearbyMerchant> findNearby(@Param("cells") Collection<String> cells, @Param("type") String type,
                                    @Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius,
                                    @Param("cuisinePattern") String cuisinePattern, @Param("namePattern") String namePattern,
                                    Pageable pageable);

    /** Ids of the open stores of a type within the radius (bounded by the area, so not paged). */
    @Query(value = "select m.merchant_id " + NEARBY_WHERE + " and m.is_open", nativeQuery = true)
    List<UUID> findOpenIdsNearby(@Param("cells") Collection<String> cells, @Param("type") String type,
                                 @Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius);

    @Query(value = "select count(*) " + NEARBY_WHERE, nativeQuery = true)
    long countNearby(@Param("cells") Collection<String> cells, @Param("type") String type,
                     @Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius);

    /** Atomic so two ratings submitted at once can't lose an update. */
    @Modifying
    @Query("update Merchant m set m.ratingSum = m.ratingSum + :stars, m.ratingCount = m.ratingCount + 1 where m.id = :id")
    void addRating(@Param("id") UUID id, @Param("stars") int stars);
}
