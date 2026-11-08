-- V11__safe_platform_connection_changes.sql
-- Two protections for a form that can be submitted twice or edited by two
-- people at once.
--
-- 1. Optimistic locking. Every save states the version it read; a save built
--    on an older version is refused instead of silently overwriting the
--    other person's work. Existing rows start at zero.
--
-- 2. Duplicate-submit protection. A create may carry an Idempotency-Key
--    header. The key is stored on the connection it created, so a retry can
--    return that connection instead of creating a second one. The partial
--    unique index allows any number of connections that did not supply a key.

ALTER TABLE platform_connections
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE platform_connections
    ADD COLUMN creation_request_key VARCHAR(100);

CREATE UNIQUE INDEX uq_platform_connections_creation_request_key
    ON platform_connections(creation_request_key)
    WHERE creation_request_key IS NOT NULL;
