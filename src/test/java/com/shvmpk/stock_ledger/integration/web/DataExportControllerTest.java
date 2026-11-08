package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.export.controller.DataExportController;
import com.shvmpk.stock_ledger.export.service.DataExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DataExportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("DataExportController Web Slice & RBAC Tests (Task 6.2)")
class DataExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DataExportService exportService;

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/export/catalog should allow OWNER and return CSV")
    void shouldAllowOwnerToExportCatalog() throws Exception {
        String mockCsv = "Product Name,SKU,Barcode,Wholesale Cost,Retail Price\n\"Heavyweight Cotton Hoodie\",HOOD-NVY-XL,5060123456789,24.00,68.00\n";
        when(exportService.exportCatalogCsv()).thenReturn(mockCsv);

        mockMvc.perform(get("/api/v1/export/catalog"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"catalog_export.csv\""))
                .andExpect(content().string(mockCsv));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/export/catalog should return 403 Forbidden for STAFF")
    void shouldForbidStaffFromExportingCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/export/catalog"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/export/orders should allow OWNER and return CSV")
    void shouldAllowOwnerToExportOrders() throws Exception {
        String mockCsv = "Order Number,Status,Customer Name,Shipping Charge,Actual Cost\nORD-2026-1001,SHIPPED,\"Astrid Lindgren\",12.50,9.80\n";
        when(exportService.exportOrdersCsv()).thenReturn(mockCsv);

        mockMvc.perform(get("/api/v1/export/orders"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"orders_export.csv\""))
                .andExpect(content().string(mockCsv));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/export/orders should return 403 Forbidden for STAFF")
    void shouldForbidStaffFromExportingOrders() throws Exception {
        mockMvc.perform(get("/api/v1/export/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/export/history should allow OWNER and return CSV")
    void shouldAllowOwnerToExportHistory() throws Exception {
        String mockCsv = "Movement ID,SKU,Quantity Delta,Reason,Reference ID,Created At\nmov_001,HOOD-NVY-XL,-1,ORDER_SHIPMENT,ord_001,2026-10-22T12:00:00Z\n";
        when(exportService.exportStockHistoryCsv()).thenReturn(mockCsv);

        mockMvc.perform(get("/api/v1/export/history"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"stock_history_export.csv\""))
                .andExpect(content().string(mockCsv));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/export/history should return 403 Forbidden for STAFF")
    void shouldForbidStaffFromExportingHistory() throws Exception {
        mockMvc.perform(get("/api/v1/export/history"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/export/catalog should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/api/v1/export/catalog"))
                .andExpect(status().isUnauthorized());
    }
}
