package com.shvmpk.stock_ledger.product.repository;

import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    @Override
    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findById(UUID id);

    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    List<ProductVariant> findAllByProductId(UUID productId);
}
