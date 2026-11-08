package com.shvmpk.stock_ledger.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared cleanup for the append-only platform connection history.
 *
 * <p>The {@code trg_platform_connection_events_append_only} trigger blocks
 * {@code DELETE}, so tests must use {@code TRUNCATE}. That escape hatch is
 * documented in {@code V10__platform_connection_events.sql} and is the only
 * supported way to reset the history between tests.</p>
 */
public final class PlatformConnectionEventTestSupport {

    private PlatformConnectionEventTestSupport() {
    }

    public static void clear(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE platform_connection_events");
    }
}
