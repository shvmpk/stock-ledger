package com.shvmpk.stock_ledger.inventory.repository;

import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryLevel, UUID> {

    @EntityGraph(attributePaths = "productVariant")
    Optional<InventoryLevel> findByProductVariantId(UUID productVariantId);

    List<InventoryLevel> findAllByProductVariantIdIn(Collection<UUID> productVariantIds);

    /**
     * Reorder guidance is only meaningful for items the merchant can still
     * sell, so retired products and variants are filtered out in the query
     * rather than being fetched and discarded later.
     */
    @EntityGraph(attributePaths = {"productVariant", "productVariant.product"})
    @Query("""
            SELECT level FROM InventoryLevel level
            WHERE level.productVariant.active = true
              AND level.productVariant.product.active = true
            """)
    List<InventoryLevel> findAllSellableWithVariantAndProduct();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
            SET reserved_quantity = reserved_quantity + :qty,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_variant_id = :variantId
              AND (on_hand_quantity - reserved_quantity) >= :qty
            """, nativeQuery = true)
    int atomicReserveStock(@Param("variantId") UUID variantId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
            SET reserved_quantity = reserved_quantity - :qty,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_variant_id = :variantId
              AND reserved_quantity >= :qty
            """, nativeQuery = true)
    int atomicReleaseStock(@Param("variantId") UUID variantId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
            SET on_hand_quantity = on_hand_quantity - :qty,
                reserved_quantity = reserved_quantity - :qty,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_variant_id = :variantId
              AND on_hand_quantity >= :qty
              AND reserved_quantity >= :qty
            """, nativeQuery = true)
    int atomicDeductForShipment(@Param("variantId") UUID variantId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
            SET on_hand_quantity = on_hand_quantity + :qty,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_variant_id = :variantId
            """, nativeQuery = true)
    int atomicReceiveStock(@Param("variantId") UUID variantId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE inventory_levels
            SET on_hand_quantity = :countedOnHand,
                updated_at = CURRENT_TIMESTAMP
            WHERE product_variant_id = :variantId
            """, nativeQuery = true)
    int atomicCorrectOnHand(@Param("variantId") UUID variantId, @Param("countedOnHand") int countedOnHand);
}
