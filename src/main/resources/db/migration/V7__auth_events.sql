-- V7__auth_events.sql
-- Append-only security audit trail for authentication and user administration.
-- Rows are snapshotted (no foreign keys) so history survives account deletion.

CREATE TABLE auth_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(40)  NOT NULL,
    failure_reason  VARCHAR(40),
    actor_user_id   UUID,
    actor_email     VARCHAR(255),
    subject_user_id UUID,
    subject_email   VARCHAR(255),
    ip_address      INET,
    user_agent      VARCHAR(512),
    request_id      VARCHAR(64),
    details         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_auth_events_type CHECK (event_type IN (
        'LOGIN_SUCCEEDED', 'LOGIN_FAILED', 'LOGOUT',
        'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'USER_CREATED', 'USER_DEACTIVATED', 'USER_REACTIVATED', 'ROLE_CHANGED'
    )),
    CONSTRAINT ck_auth_events_failure_reason CHECK (failure_reason IS NULL OR failure_reason IN (
        'INVALID_PASSWORD', 'UNKNOWN_EMAIL', 'INACTIVE_ACCOUNT'
    ))
);

-- Append-only enforcement lives in the database, not only in Java.
-- TRUNCATE is deliberately not blocked: it is the documented test and
-- break-glass purge escape hatch (see the retention runbook).
CREATE OR REPLACE FUNCTION reject_auth_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'auth_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auth_events_append_only
    BEFORE UPDATE OR DELETE ON auth_events
    FOR EACH ROW EXECUTE FUNCTION reject_auth_event_mutation();

CREATE INDEX idx_auth_events_occurred_at
    ON auth_events(occurred_at DESC);

CREATE INDEX idx_auth_events_type_occurred_at
    ON auth_events(event_type, occurred_at DESC);

CREATE INDEX idx_auth_events_actor_user
    ON auth_events(actor_user_id, occurred_at DESC);

CREATE INDEX idx_auth_events_subject_user
    ON auth_events(subject_user_id);

CREATE INDEX idx_auth_events_actor_email_lower
    ON auth_events(lower(actor_email));
