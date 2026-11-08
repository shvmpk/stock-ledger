-- V4__init_stock_counts_and_platforms.sql
-- StockLedger Shelf Stock Auditing, External Platform Connections & Webhook Idempotency

CREATE TABLE stock_counts (
    id UUID PRIMARY KEY,
    status VARCHAR(50) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    conducted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_stock_counts_status ON stock_counts(status);
CREATE INDEX idx_stock_counts_started_at ON stock_counts(started_at);

CREATE TABLE stock_count_items (
    id UUID PRIMARY KEY,
    stock_count_id UUID NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    expected_on_hand INT NOT NULL,
    counted_on_hand INT NOT NULL,
    variance INT NOT NULL,
    notes TEXT
);

CREATE INDEX idx_stock_count_items_count_id ON stock_count_items(stock_count_id);
CREATE INDEX idx_stock_count_items_variant_id ON stock_count_items(product_variant_id);

CREATE TABLE platform_connections (
    id UUID PRIMARY KEY,
    platform_type VARCHAR(50) NOT NULL CHECK (platform_type IN ('SHOPIFY', 'WOOCOMMERCE', 'ETSY')),
    api_endpoint VARCHAR(255) NOT NULL,
    webhook_secret_hash VARCHAR(255) NOT NULL,
    encrypted_api_credentials TEXT,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    sync_status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS' CHECK (sync_status IN ('SUCCESS', 'FAILED', 'RUNNING')),
    last_error_message TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_connections_type ON platform_connections(platform_type);
CREATE INDEX idx_platform_connections_active ON platform_connections(active);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_hash VARCHAR(255),
    response_status INT NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_records_key ON idempotency_records(idempotency_key);
CREATE INDEX idx_idempotency_records_expires ON idempotency_records(expires_at);
