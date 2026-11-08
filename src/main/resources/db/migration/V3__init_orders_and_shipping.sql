-- V3__init_orders_and_shipping.sql
-- StockLedger Order Management, Line-Item Returns & Shipping Price List Schema

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    source VARCHAR(50) NOT NULL CHECK (source IN ('MANUAL', 'CSV', 'WEBHOOK', 'PLATFORM_SYNC')),
    external_order_id VARCHAR(100),
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'WAITING_FOR_STOCK', 'READY_TO_SHIP', 'SHIPPED', 'RETURNED', 'PARTIALLY_RETURNED', 'CANCELLED')),
    customer_name VARCHAR(150) NOT NULL,
    customer_email VARCHAR(255),
    shipping_address TEXT NOT NULL,
    customer_shipping_charge NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (customer_shipping_charge >= 0),
    actual_shipping_cost NUMERIC(10, 2) CHECK (actual_shipping_cost >= 0),
    carrier VARCHAR(50),
    parcel_size VARCHAR(50),
    tracking_number VARCHAR(100),
    tracking_url TEXT,
    shipped_at TIMESTAMP WITH TIME ZONE,
    returned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_variant_id ON order_items(product_variant_id);

CREATE TABLE order_returns (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    processed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    return_reason VARCHAR(255),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_returns_order_id ON order_returns(order_id);

CREATE TABLE order_return_items (
    id UUID PRIMARY KEY,
    order_return_id UUID NOT NULL REFERENCES order_returns(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    condition VARCHAR(50) NOT NULL CHECK (condition IN ('SELLABLE', 'DAMAGED')),
    restocked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_order_return_items_return_id ON order_return_items(order_return_id);

CREATE TABLE shipping_price_list (
    id UUID PRIMARY KEY,
    carrier VARCHAR(50) NOT NULL,
    parcel_size VARCHAR(50) NOT NULL,
    cost NUMERIC(10, 2) NOT NULL CHECK (cost >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carrier_parcel UNIQUE (carrier, parcel_size)
);

CREATE INDEX idx_shipping_price_lookup ON shipping_price_list(carrier, parcel_size);
