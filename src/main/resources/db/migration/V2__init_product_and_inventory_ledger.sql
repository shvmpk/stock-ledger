-- V2__init_product_and_inventory_ledger.sql
-- StockLedger Product Catalog & Double-Entry Inventory Ledger Schema

CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_category ON products(category);

CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    barcode VARCHAR(100),
    option_name VARCHAR(100),
    option_value VARCHAR(100),
    cost_price NUMERIC(10, 2) NOT NULL CHECK (cost_price >= 0),
    selling_price NUMERIC(10, 2) NOT NULL CHECK (selling_price >= 0),
    weight_grams INT NOT NULL DEFAULT 0 CHECK (weight_grams >= 0),
    min_stock_threshold INT NOT NULL DEFAULT 5 CHECK (min_stock_threshold >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_variants_sku ON product_variants(sku);
CREATE INDEX idx_product_variants_barcode ON product_variants(barcode);
CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);

CREATE TABLE inventory_levels (
    id UUID PRIMARY KEY,
    product_variant_id UUID NOT NULL UNIQUE REFERENCES product_variants(id) ON DELETE CASCADE,
    on_hand_quantity INT NOT NULL DEFAULT 0 CHECK (on_hand_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_levels_variant ON inventory_levels(product_variant_id);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    delta_on_hand INT NOT NULL,
    delta_reserved INT NOT NULL,
    reason VARCHAR(50) NOT NULL CHECK (reason IN ('RECEIVE', 'ORDER_RESERVED', 'ORDER_SHIPPED', 'ORDER_CANCELLED', 'RETURN_RESTOCK', 'RETURN_DAMAGED', 'AUDIT_CORRECTION')),
    reference_type VARCHAR(50),
    reference_id UUID,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_movements_variant ON stock_movements(product_variant_id);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at);
CREATE INDEX idx_stock_movements_reason ON stock_movements(reason);
