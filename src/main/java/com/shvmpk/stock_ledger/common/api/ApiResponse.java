package com.shvmpk.stock_ledger.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        PaginationMeta pagination,
        ResponseMeta meta
) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(true, data, null, ResponseMeta.of(requestId));
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, ResponseMeta.now());
    }

    public static <T> ApiResponse<T> paginated(T data, PaginationMeta pagination, String requestId) {
        return new ApiResponse<>(true, data, pagination, ResponseMeta.of(requestId));
    }

    public static <T> ApiResponse<T> paginated(T data, PaginationMeta pagination) {
        return new ApiResponse<>(true, data, pagination, ResponseMeta.now());
    }
}
