package com.shvmpk.stock_ledger.product.entity;

/**
 * The catalogue action that produced a history entry. Retirement and
 * restoration are separate kinds because they are separate decisions.
 */

public enum ProductCatalogChangeType {
    RETIRED,
    RESTORED
}
