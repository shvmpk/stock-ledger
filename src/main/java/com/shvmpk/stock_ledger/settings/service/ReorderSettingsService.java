package com.shvmpk.stock_ledger.settings.service;

import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.settings.dto.ReorderSettingsResponse;
import com.shvmpk.stock_ledger.settings.entity.ReorderSettings;
import com.shvmpk.stock_ledger.settings.repository.ReorderSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReorderSettingsService {

    private final ReorderSettingsRepository reorderSettingsRepository;

    @Transactional(readOnly = true)
    public ReorderSettingsResponse getSettings() {
        return toResponse(findSettings());
    }

    @Transactional
    public ReorderSettingsResponse updateLeadTimeDays(int leadTimeDays, UUID actorId) {
        ReorderSettings settings = findSettings();
        settings.updateLeadTime(leadTimeDays, actorId);
        return toResponse(settings);
    }

    private ReorderSettings findSettings() {
        return reorderSettingsRepository.findById(ReorderSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ReorderSettings",
                        ReorderSettings.SINGLETON_ID
                ));
    }

    private ReorderSettingsResponse toResponse(ReorderSettings settings) {
        return new ReorderSettingsResponse(
                settings.getLeadTimeDays(),
                settings.getUpdatedAt()
        );
    }
}
