-- V10__platform_connection_events.sql
-- Append-only history for platform connection changes. One row per save.
-- Rows snapshot the connection id and platform type rather than joining, so
-- the record survives any future retirement of a connection.

CREATE TABLE platform_connection_events (
    id                     UUID PRIMARY KEY,
    platform_connection_id UUID         NOT NULL,
    platform_type          VARCHAR(50)  NOT NULL,
    change_type            VARCHAR(40)  NOT NULL,
    actor_user_id          UUID,
    actor_email            VARCHAR(255),
    fields                 JSONB        NOT NULL DEFAULT '[]'::jsonb,
    occurred_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_platform_connection_events_change_type CHECK (change_type IN (
        'CREATED', 'UPDATED', 'PAUSED', 'RESUMED'
    ))
);

-- Append-only enforcement lives in the database, not only in Java.
-- TRUNCATE is deliberately not blocked: it is the documented test and
-- break-glass purge escape hatch, matching auth_events.
CREATE OR REPLACE FUNCTION reject_platform_connection_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'platform_connection_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_platform_connection_events_append_only
    BEFORE UPDATE OR DELETE ON platform_connection_events
    FOR EACH ROW EXECUTE FUNCTION reject_platform_connection_event_mutation();

CREATE INDEX idx_platform_connection_events_connection_occurred_at
    ON platform_connection_events(platform_connection_id, occurred_at DESC, id DESC);
