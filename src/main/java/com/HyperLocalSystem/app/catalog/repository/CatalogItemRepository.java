package com.HyperLocalSystem.app.catalog.repository;

import com.HyperLocalSystem.app.catalog.entity.CatalogItem;
import com.HyperLocalSystem.app.catalog.entity.ItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    String GROCERY_WHERE = """
            from catalog_items i
            where i.merchant_id in (:merchantIds)
              and i.item_type = 'GROCERY'
              and i.available
              and (CAST(:pattern AS text) is null or i.name ilike :pattern or i.brand ilike :pattern)
              and (CAST(:category AS text) is null or i.category = :category)
            """;

    Page<CatalogItem> findByMerchantIdOrderByCategoryAscNameAsc(UUID merchantId, Pageable pageable);

    List<CatalogItem> findByMerchantIdOrderByCategoryAscNameAsc(UUID merchantId);

    Optional<CatalogItem> findByIdAndMerchantId(UUID id, UUID merchantId);

    List<CatalogItem> findTop20ByMerchantIdInAndTypeAndAvailableTrueAndNameContainingIgnoreCase(
            Collection<UUID> merchantIds, ItemType type, String name);

    /** "Add a meal" suggestions: the priciest dishes nearby (highest margin for the platform). */
    List<CatalogItem> findTop5ByMerchantIdInAndTypeAndAvailableTrueOrderByPriceDesc(Collection<UUID> merchantIds, ItemType type);

    /**
     * The virtual-warehouse catalogue: grocery listings of the given Kiranas, one row per product
     * (name + pack size), keeping the cheapest listing.
     */
    @Query(value = """
            select * from (
                select distinct on (lower(i.name), coalesce(i.pack_size, ''))
                       i.item_id as "id", i.name as "name", i.brand as "brand", i.pack_size as "packSize",
                       i.mrp as "mrp", i.price as "price", i.category as "category", i.zone as "zone",
                       i.image_url as "imageUrl"
            """ + GROCERY_WHERE + """
                order by lower(i.name), coalesce(i.pack_size, ''), i.price
            ) g
            order by g."category", g."name"
            """, countQuery = """
            select count(*) from (
                select distinct lower(i.name), coalesce(i.pack_size, '')
            """ + GROCERY_WHERE + """
            ) c
            """, nativeQuery = true)
    Page<GroceryRow> findGroceries(@Param("merchantIds") Collection<UUID> merchantIds, @Param("pattern") String pattern,
                                   @Param("category") String category, Pageable pageable);
}
