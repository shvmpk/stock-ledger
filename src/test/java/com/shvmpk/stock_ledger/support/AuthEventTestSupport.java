package com.shvmpk.stock_ledger.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared cleanup for the append-only audit tables.
 *
 * <p>The {@code trg_auth_events_append_only} trigger blocks {@code DELETE}, so
 * tests must use {@code TRUNCATE}. That escape hatch is documented in
 * {@code V7__auth_events.sql} and is the only supported way to reset the
 * security log between tests. Do not add delete-based cleanup for this table.</p>
 */
public final class AuthEventTestSupport {

    private AuthEventTestSupport() {
    }

    public static void clear(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE auth_events");
    }
}
