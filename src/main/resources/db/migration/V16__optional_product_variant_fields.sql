-- V16__optional_product_variant_fields.sql
-- Cost, weight, and reorder level are optional. Unknown values stay NULL so the
-- interface can show an honest gap instead of a fabricated zero.
--
-- Legacy zeros for cost and weight were forced by the old service defaults, so
-- they are converted to unknown. A legacy reorder level of 5 is a real warning
-- level and is deliberately kept.

ALTER TABLE product_variants
    ALTER COLUMN cost_price DROP NOT NULL,
    ALTER COLUMN weight_grams DROP NOT NULL,
    ALTER COLUMN weight_grams DROP DEFAULT,
    ALTER COLUMN min_stock_threshold DROP NOT NULL,
    ALTER COLUMN min_stock_threshold DROP DEFAULT;

UPDATE product_variants
SET cost_price = NULL
WHERE cost_price = 0;

UPDATE product_variants
SET weight_grams = NULL
WHERE weight_grams = 0;
