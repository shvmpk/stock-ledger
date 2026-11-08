-- Retiring is a catalogue action, not a deletion. History must survive, so
-- every retirement and restoration appends an immutable row here. Snapshots of
-- the product name and variant SKU mean the record still reads correctly after
-- the catalogue is edited again.

CREATE TABLE product_catalog_events (
    id            UUID PRIMARY KEY,
    product_id    UUID         NOT NULL,
    variant_id    UUID,
    scope         VARCHAR(20)  NOT NULL,
    change_type   VARCHAR(20)  NOT NULL,
    product_name  VARCHAR(255) NOT NULL,
    variant_sku   VARCHAR(100),
    actor_user_id UUID,
    actor_email   VARCHAR(255),
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_product_catalog_events_scope CHECK (scope IN ('PRODUCT', 'VARIANT')),
    CONSTRAINT ck_product_catalog_events_change_type CHECK (
        change_type IN ('RETIRED', 'RESTORED')
    ),
    CONSTRAINT ck_product_catalog_events_variant CHECK (
        (scope = 'PRODUCT' AND variant_id IS NULL AND variant_sku IS NULL)
        OR (scope = 'VARIANT' AND variant_id IS NOT NULL AND variant_sku IS NOT NULL)
    )
);

-- Append-only enforcement lives in the database, not only in Java.
-- TRUNCATE is deliberately not blocked: it is the documented test and
-- break-glass purge escape hatch, matching auth_events and platform history.
CREATE OR REPLACE FUNCTION reject_product_catalog_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'product_catalog_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_catalog_events_append_only
    BEFORE UPDATE OR DELETE ON product_catalog_events
    FOR EACH ROW EXECUTE FUNCTION reject_product_catalog_event_mutation();

CREATE INDEX idx_product_catalog_events_product_occurred_at
    ON product_catalog_events(product_id, occurred_at DESC, id DESC);

CREATE INDEX idx_product_catalog_events_variant_occurred_at
    ON product_catalog_events(variant_id, occurred_at DESC, id DESC)
    WHERE variant_id IS NOT NULL;
