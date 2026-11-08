-- V13__platform_connection_tests.sql
-- A connection can now be tested on demand, and inbound-only channels report
-- when their last order actually arrived. Test results are visible on the
-- connection card and in history; the history row records the outcome and the
-- plain-language detail, never a credential.

ALTER TABLE platform_connections
    ADD COLUMN last_tested_at TIMESTAMPTZ,
    ADD COLUMN last_test_outcome VARCHAR(40),
    ADD COLUMN last_test_http_status INTEGER,
    ADD COLUMN last_test_detail VARCHAR(500),
    ADD COLUMN last_inbound_order_at TIMESTAMPTZ;

ALTER TABLE platform_connection_events
    ADD COLUMN outcome VARCHAR(40),
    ADD COLUMN detail VARCHAR(500);

-- The history check constraint predates the TESTED kind, so widen it in the
-- same migration that introduces the kind. Existing rows are unaffected.
ALTER TABLE platform_connection_events
    DROP CONSTRAINT ck_platform_connection_events_change_type;

ALTER TABLE platform_connection_events
    ADD CONSTRAINT ck_platform_connection_events_change_type CHECK (change_type IN (
        'CREATED', 'UPDATED', 'PAUSED', 'RESUMED', 'TESTED'
    )),
    ADD CONSTRAINT ck_platform_connection_events_outcome CHECK (
        (change_type = 'TESTED' AND outcome IS NOT NULL)
        OR (change_type <> 'TESTED' AND outcome IS NULL)
    );
