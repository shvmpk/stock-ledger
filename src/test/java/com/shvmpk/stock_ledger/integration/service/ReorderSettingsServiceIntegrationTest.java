package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.settings.dto.ReorderSettingsResponse;
import com.shvmpk.stock_ledger.settings.entity.ReorderSettings;
import com.shvmpk.stock_ledger.settings.repository.ReorderSettingsRepository;
import com.shvmpk.stock_ledger.settings.service.ReorderSettingsService;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Reorder Settings Service Integration Tests (Task 3.1)")
class ReorderSettingsServiceIntegrationTest {

    @Autowired
    private ReorderSettingsService reorderSettingsService;

    @Autowired
    private ReorderSettingsRepository reorderSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Reads the seeded fourteen-day supplier lead time")
    void shouldReadSeededLeadTime() {
        ReorderSettingsResponse settings = reorderSettingsService.getSettings();

        assertThat(settings.leadTimeDays()).isEqualTo(14);
        assertThat(settings.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Persists an owner-adjusted lead time and records the actor")
    void shouldPersistLeadTimeAndActor() {
        User owner = userRepository.saveAndFlush(User.create(
                UUID.randomUUID(),
                "mara.ellison@northwoodgoods.co.uk",
                "$2a$10$7EqJtq98hPqEX7fNZaFWoO5e5Q0h5W5b8k1w5k5Q0Q0Q0Q0Q0Q0Q0",
                "Mara Ellison",
                Role.OWNER
        ));

        ReorderSettingsResponse updated = reorderSettingsService.updateLeadTimeDays(21, owner.getId());

        assertThat(updated.leadTimeDays()).isEqualTo(21);
        ReorderSettings stored = reorderSettingsRepository.findById(ReorderSettings.SINGLETON_ID)
                .orElseThrow();
        assertThat(stored.getLeadTimeDays()).isEqualTo(21);
        assertThat(stored.getUpdatedBy()).isEqualTo(owner.getId());
    }

    @Test
    @DisplayName("Refuses a lead time outside one to three hundred and sixty-five days")
    void shouldRefuseInvalidLeadTime() {
        assertThatThrownBy(() -> reorderSettingsService.updateLeadTimeDays(0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 365");
        assertThatThrownBy(() -> reorderSettingsService.updateLeadTimeDays(366, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 365");

        assertThat(reorderSettingsService.getSettings().leadTimeDays()).isEqualTo(14);
    }
}
