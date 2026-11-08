package com.shvmpk.stock_ledger.platform.repository;

import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlatformConnectionEventRepository extends JpaRepository<PlatformConnectionEvent, UUID> {

    Page<PlatformConnectionEvent> findByPlatformConnectionId(UUID platformConnectionId, Pageable pageable);
}
