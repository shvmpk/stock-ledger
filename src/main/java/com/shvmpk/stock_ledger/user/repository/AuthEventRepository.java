package com.shvmpk.stock_ledger.user.repository;

import com.shvmpk.stock_ledger.user.entity.AuthEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID>, JpaSpecificationExecutor<AuthEvent> {
}
