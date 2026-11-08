-- A single row holds the merchant's reorder assumptions. The lead time is an
-- estimate input, not a purchase-order commitment, so it is stored separately
-- from the catalogue and remains adjustable by the owner.
CREATE TABLE reorder_settings (
    id SMALLINT PRIMARY KEY,
    lead_time_days INT NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reorder_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_reorder_settings_lead_time CHECK (lead_time_days BETWEEN 1 AND 365),
    CONSTRAINT fk_reorder_settings_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO reorder_settings (id, lead_time_days, updated_at)
VALUES (1, 14, CURRENT_TIMESTAMP);

-- Reorder velocity reads shipped orders and processed returns over a rolling
-- ninety-day window; both lookups benefit from a purpose-built index.
CREATE INDEX idx_orders_shipped_at ON orders(shipped_at);
CREATE INDEX idx_order_returns_processed_at ON order_returns(processed_at);
