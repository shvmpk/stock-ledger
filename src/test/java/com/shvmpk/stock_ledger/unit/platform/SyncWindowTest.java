package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.adapter.SyncWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SyncWindow Unit Tests (30-day first window, 5-minute overlap)")
class SyncWindowTest {

    @Test
    @DisplayName("First sync should look back 30 days")
    void shouldUseThirtyDayFirstWindow() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-23T12:00:00Z");

        SyncWindow window = SyncWindow.forConnection(null, now);

        assertThat(window.from()).isEqualTo(now.minusDays(30));
        assertThat(window.to()).isEqualTo(now);
    }

    @Test
    @DisplayName("Subsequent sync should overlap the previous run by 5 minutes")
    void shouldOverlapPreviousSyncByFiveMinutes() {
        OffsetDateTime previous = OffsetDateTime.parse("2026-10-22T12:00:00Z");
        OffsetDateTime now = OffsetDateTime.parse("2026-10-23T12:00:00Z");

        SyncWindow window = SyncWindow.forConnection(previous, now);

        assertThat(window.from()).isEqualTo(previous.minusMinutes(5));
        assertThat(window.to()).isEqualTo(now);
    }
}
