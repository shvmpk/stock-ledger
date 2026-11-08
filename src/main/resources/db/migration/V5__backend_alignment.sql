-- V5__backend_alignment.sql
-- Backend alignment: stock-count sessions, return notes, queue reads,
-- platform credential storage, deactivation, and sync status.

-- 1. Stock count session integrity and persisted reservation deficit.
ALTER TABLE stock_count_items
    ADD CONSTRAINT uq_stock_count_items_session_variant
    UNIQUE (stock_count_id, product_variant_id);

ALTER TABLE stock_count_items
    ADD COLUMN has_reservation_deficit BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reservation_deficit INT NOT NULL DEFAULT 0;

-- 2. Return operator notes.
ALTER TABLE order_returns
    ADD COLUMN operator_notes TEXT;

-- 3. Queue read performance.
CREATE INDEX idx_orders_status_created_at
    ON orders(status, created_at DESC);

-- 4. The HMAC secret is a secret, not a hash, and must be ciphertext.
ALTER TABLE platform_connections
    RENAME COLUMN webhook_secret_hash TO webhook_secret_encrypted;

ALTER TABLE platform_connections
    ALTER COLUMN webhook_secret_encrypted TYPE TEXT;

-- 5. Deactivation timestamp for soft-deleted connections.
ALTER TABLE platform_connections
    ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;

-- 6. New connections are PENDING until the first sync.
ALTER TABLE platform_connections
    DROP CONSTRAINT IF EXISTS platform_connections_sync_status_check;

ALTER TABLE platform_connections
    ADD CONSTRAINT ck_platform_connections_sync_status
    CHECK (sync_status IN ('PENDING', 'SUCCESS', 'FAILED', 'RUNNING'));

-- 7. Legacy values are unusable: the old column held a hash and the
--    credential column held plaintext. Force reconfiguration.
UPDATE platform_connections
SET active = FALSE,
    deactivated_at = NOW(),
    webhook_secret_encrypted = '',
    encrypted_api_credentials = NULL
WHERE webhook_secret_encrypted <> '';

-- 8. At most one active connection per platform type.
--    Created after the legacy reset so duplicate active legacy rows cannot
--    prevent the migration from completing.
CREATE UNIQUE INDEX uq_platform_connections_active_type
    ON platform_connections(platform_type)
    WHERE active = TRUE;
