package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.analytics.controller.AnalyticsController;
import com.shvmpk.stock_ledger.analytics.dto.ShippingSummaryDto;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AnalyticsController Web Slice & RBAC Tests (Task 6.1)")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShippingAnalyticsService analyticsService;

    @MockitoBean
    private ReorderService reorderService;

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/analytics/shipping-summary should return 200 OK with summary data for OWNER")
    void shouldAllowOwnerToViewShippingSummary() throws Exception {
        ShippingSummaryDto summary = new ShippingSummaryDto(
                "2026-10",
                42,
                new BigDecimal("273.00"),
                new BigDecimal("252.00"),
                new BigDecimal("21.00")
        );

        when(analyticsService.getMonthlyShippingSummaries("2026-10")).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/analytics/shipping-summary")
                        .param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].period").value("2026-10"))
                .andExpect(jsonPath("$.data[0].totalOrdersShipped").value(42))
                .andExpect(jsonPath("$.data[0].totalShippingCharged").value(273.00))
                .andExpect(jsonPath("$.data[0].totalShippingCost").value(252.00))
                .andExpect(jsonPath("$.data[0].netMargin").value(21.00));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/analytics/shipping-summary should return 403 Forbidden for STAFF")
    void shouldForbidStaffFromViewingShippingSummary() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/shipping-summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/shipping-summary should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedShippingSummary() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/shipping-summary"))
                .andExpect(status().isUnauthorized());
    }
}
