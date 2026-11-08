package com.shvmpk.stock_ledger.common.api;

public record PaginationMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasMore
) {
    public static PaginationMeta of(int page, int size, long totalElements) {
        int calculatedTotalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean calculatedHasMore = (page + 1) < calculatedTotalPages;
        return new PaginationMeta(page, size, totalElements, calculatedTotalPages, calculatedHasMore);
    }
}
