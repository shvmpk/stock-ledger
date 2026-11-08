package com.shvmpk.stock_ledger.unit.common;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.common.api.PaginationMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse & PaginationMeta Envelope Unit Tests")
class ApiResponseTest {

    record SampleDto(String sku, int quantity) {}

    @Test
    @DisplayName("Single resource response envelope should contain success, data, and meta")
    void shouldCreateSingleResourceEnvelope() {
        SampleDto item = new SampleDto("HOOD-NVY-XL", 2);
        ApiResponse<SampleDto> response = ApiResponse.ok(item, "req_12345");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(item);
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().requestId()).isEqualTo("req_12345");
        assertThat(response.meta().timestamp()).isNotNull();
        assertThat(response.pagination()).isNull();
    }

    @Test
    @DisplayName("Paginated collection response envelope should contain data and pagination metadata")
    void shouldCreatePaginatedCollectionEnvelope() {
        List<SampleDto> items = List.of(
                new SampleDto("HOOD-NVY-XL", 2),
                new SampleDto("HOOD-NVY-M", 5)
        );
        PaginationMeta pagination = PaginationMeta.of(0, 20, 45);

        ApiResponse<List<SampleDto>> response = ApiResponse.paginated(items, pagination, "req_9999");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(2);
        assertThat(response.pagination()).isNotNull();
        assertThat(response.pagination().page()).isEqualTo(0);
        assertThat(response.pagination().size()).isEqualTo(20);
        assertThat(response.pagination().totalElements()).isEqualTo(45L);
        assertThat(response.pagination().totalPages()).isEqualTo(3);
        assertThat(response.pagination().hasMore()).isTrue();
        assertThat(response.meta().requestId()).isEqualTo("req_9999");
    }

    @Test
    @DisplayName("PaginationMeta calculation edge cases")
    void shouldCalculatePaginationCorrectly() {
        // Exactly one page
        PaginationMeta p1 = PaginationMeta.of(0, 10, 10);
        assertThat(p1.totalPages()).isEqualTo(1);
        assertThat(p1.hasMore()).isFalse();

        // Zero elements
        PaginationMeta p2 = PaginationMeta.of(0, 10, 0);
        assertThat(p2.totalPages()).isEqualTo(0);
        assertThat(p2.hasMore()).isFalse();

        // Last page of multiple pages
        PaginationMeta p3 = PaginationMeta.of(2, 10, 25);
        assertThat(p3.totalPages()).isEqualTo(3);
        assertThat(p3.hasMore()).isFalse();
    }
}
