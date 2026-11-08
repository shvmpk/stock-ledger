package com.shvmpk.stock_ledger.settings.repository;

import com.shvmpk.stock_ledger.settings.entity.ReorderSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReorderSettingsRepository extends JpaRepository<ReorderSettings, Short> {
}
