package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.service.PlatformSyncScheduler;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("PlatformSyncScheduler Configuration Tests")
class PlatformSyncSchedulerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("app.platform.sync.http-timeout=10s")
            .withUserConfiguration(SchedulerTestConfig.class)
            .withUserConfiguration(PlatformSyncScheduler.class);

    @Test
    @DisplayName("Should not register the scheduled sync job when disabled")
    void shouldNotRegisterSchedulerWhenDisabled() {
        runner.withPropertyValues("app.platform.sync.scheduled-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PlatformSyncScheduler.class));
    }

    @Test
    @DisplayName("Should register the scheduled sync job when enabled")
    void shouldRegisterSchedulerWhenEnabled() {
        runner.withPropertyValues("app.platform.sync.scheduled-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PlatformSyncScheduler.class));
    }

    @Configuration
    static class SchedulerTestConfig {

        @Bean
        PlatformSyncService platformSyncService() {
            return mock(PlatformSyncService.class);
        }
    }
}
