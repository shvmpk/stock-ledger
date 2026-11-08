-- V6__universal_platform_connections.sql
-- StockLedger Universal Platform Connections & Dynamic Webhooks

-- 1. Remove hardcoded enum check constraint to allow custom platform types
ALTER TABLE platform_connections
    DROP CONSTRAINT IF EXISTS platform_connections_platform_type_check;

-- 2. Add merchant-defined naming and slug for dedicated webhook routing
ALTER TABLE platform_connections
    ADD COLUMN display_name VARCHAR(100),
    ADD COLUMN slug VARCHAR(100);

-- 3. Populate default display names and slugs for existing seeded connections
UPDATE platform_connections
SET display_name = 'Shopify Store',
    slug = 'shopify'
WHERE platform_type = 'SHOPIFY' AND slug IS NULL;

UPDATE platform_connections
SET display_name = 'WooCommerce Store',
    slug = 'woocommerce'
WHERE platform_type = 'WOOCOMMERCE' AND slug IS NULL;

UPDATE platform_connections
SET display_name = 'Etsy Store',
    slug = 'etsy'
WHERE platform_type = 'ETSY' AND slug IS NULL;

-- 4. Enforce unique slugs among active connections
CREATE UNIQUE INDEX uq_platform_connections_active_slug
    ON platform_connections(slug)
    WHERE active = TRUE;

