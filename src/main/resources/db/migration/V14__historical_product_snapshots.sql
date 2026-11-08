-- History must not change when the catalogue is edited.
-- Order lines snapshot the SKU; stock movements snapshot the SKU and product
-- name. Existing rows are backfilled from the current catalogue.

ALTER TABLE order_items
    ADD COLUMN sku VARCHAR(100);

UPDATE order_items oi
SET sku = pv.sku
FROM product_variants pv
WHERE pv.id = oi.product_variant_id;

ALTER TABLE order_items
    ALTER COLUMN sku SET NOT NULL;

CREATE INDEX idx_order_items_sku_snapshot ON order_items(sku);

ALTER TABLE stock_movements
    ADD COLUMN sku VARCHAR(100),
    ADD COLUMN product_name VARCHAR(255);

UPDATE stock_movements sm
SET sku = pv.sku,
    product_name = p.name
FROM product_variants pv
JOIN products p ON p.id = pv.product_id
WHERE pv.id = sm.product_variant_id;

ALTER TABLE stock_movements
    ALTER COLUMN sku SET NOT NULL,
    ALTER COLUMN product_name SET NOT NULL;

CREATE INDEX idx_stock_movements_sku_snapshot ON stock_movements(sku);
