package com.shvmpk.stock_ledger.product.repository;

import com.shvmpk.stock_ledger.product.entity.ProductCatalogEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductCatalogEventRepository extends JpaRepository<ProductCatalogEvent, UUID> {

    List<ProductCatalogEvent> findByProductIdOrderByOccurredAtAscIdAsc(UUID productId);
}
