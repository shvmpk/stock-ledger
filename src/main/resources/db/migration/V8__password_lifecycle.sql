-- V8__password_lifecycle.sql
-- Password lifecycle: temporary passwords issued by an owner and the timestamp
-- of the last credential change. Existing accounts keep their passwords and
-- are not forced to rotate.

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- V1 users predate the column; their password was set when the account was
-- created. This is the only backfill in this workstream.
UPDATE users SET password_changed_at = created_at;
