package com.shvmpk.stock_ledger.inventory.repository;

import com.shvmpk.stock_ledger.inventory.entity.StockCountItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockCountItemRepository extends JpaRepository<StockCountItem, UUID> {

    List<StockCountItem> findAllByStockCountId(UUID stockCountId);

    boolean existsByStockCountIdAndProductVariantId(UUID stockCountId, UUID productVariantId);
}
