package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.analytics.controller.AnalyticsController;
import com.shvmpk.stock_ledger.analytics.dto.ReorderDetailResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderHistoryStatus;
import com.shvmpk.stock_ledger.analytics.dto.ReorderItemResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderListResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderReturnLineResponse;
import com.shvmpk.stock_ledger.analytics.dto.ReorderSaleLineResponse;
import com.shvmpk.stock_ledger.analytics.service.ReorderService;
import com.shvmpk.stock_ledger.analytics.service.ShippingAnalyticsService;
import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Reorder Analytics Controller Web Slice & RBAC Tests (Task 3.1)")
class ReorderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReorderService reorderService;

    @MockitoBean
    private ShippingAnalyticsService shippingAnalyticsService;

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/analytics/reorder should expose assumptions and items to STAFF")
    void shouldAllowStaffToViewReorderList() throws Exception {
        ReorderItemResponse item = new ReorderItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Heavyweight Cotton Hoodie",
                "Size",
                "XL",
                "HOOD-NVY-XL",
                "5901234123457",
                8,
                5,
                3,
                5,
                54,
                17,
                OffsetDateTime.parse("2026-09-02T10:15:00Z"),
                5,
                14,
                ReorderHistoryStatus.SUFFICIENT
        );
        when(reorderService.getReorderList()).thenReturn(new ReorderListResponse(
                90,
                14,
                14,
                21,
                5,
                OffsetDateTime.parse("2026-11-01T09:00:00Z"),
                List.of(item)
        ));

        mockMvc.perform(get("/api/v1/analytics/reorder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.salesWindowDays").value(90))
                .andExpect(jsonPath("$.data.leadTimeDays").value(14))
                .andExpect(jsonPath("$.data.targetCoverDays").value(14))
                .andExpect(jsonPath("$.data.minimumHistoryDays").value(21))
                .andExpect(jsonPath("$.data.minimumUnitsSold").value(5))
                .andExpect(jsonPath("$.data.items[0].sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data.items[0].unitsSold").value(54))
                .andExpect(jsonPath("$.data.items[0].daysOfCover").value(5))
                .andExpect(jsonPath("$.data.items[0].suggestedQuantity").value(14))
                .andExpect(jsonPath("$.data.items[0].historyStatus").value("SUFFICIENT"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/analytics/reorder/{variantId} should expose traceable sales lines")
    void shouldAllowStaffToTraceReorderEstimate() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(reorderService.getDetail(variantId)).thenReturn(new ReorderDetailResponse(
                variantId,
                "Heavyweight Cotton Hoodie",
                "Size",
                "XL",
                "HOOD-NVY-XL",
                90,
                OffsetDateTime.parse("2026-08-03T09:00:00Z"),
                OffsetDateTime.parse("2026-11-01T09:00:00Z"),
                60,
                6,
                54,
                List.of(new ReorderSaleLineResponse(
                        UUID.randomUUID(),
                        "ORD-2026-1078",
                        OffsetDateTime.parse("2026-10-22T14:30:00Z"),
                        20,
                        "SHIPPED"
                )),
                List.of(new ReorderReturnLineResponse(
                        UUID.randomUUID(),
                        "ORD-2026-1078",
                        OffsetDateTime.parse("2026-10-27T11:05:00Z"),
                        6,
                        "SELLABLE"
                ))
        ));

        mockMvc.perform(get("/api/v1/analytics/reorder/{variantId}", variantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data.unitsShipped").value(60))
                .andExpect(jsonPath("$.data.unitsReturned").value(6))
                .andExpect(jsonPath("$.data.netUnitsSold").value(54))
                .andExpect(jsonPath("$.data.sales[0].orderNumber").value("ORD-2026-1078"))
                .andExpect(jsonPath("$.data.sales[0].quantity").value(20))
                .andExpect(jsonPath("$.data.returns[0].quantity").value(6));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/reorder should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedReorderRequest() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/reorder"))
                .andExpect(status().isUnauthorized());
    }
}
