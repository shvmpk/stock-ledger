package com.shvmpk.stock_ledger.order.repository;

import com.shvmpk.stock_ledger.order.entity.OrderReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderReturnRepository extends JpaRepository<OrderReturn, UUID> {
    List<OrderReturn> findAllByOrderId(UUID orderId);

    List<OrderReturn> findAllByOrderIdOrderByProcessedAtAscIdAsc(UUID orderId);
}
