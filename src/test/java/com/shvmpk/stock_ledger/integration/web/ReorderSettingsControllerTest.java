package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.settings.controller.ReorderSettingsController;
import com.shvmpk.stock_ledger.settings.dto.ReorderSettingsResponse;
import com.shvmpk.stock_ledger.settings.service.ReorderSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReorderSettingsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Reorder Settings Controller Web Slice & RBAC Tests (Task 3.1)")
class ReorderSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReorderSettingsService reorderSettingsService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/settings/reorder should be readable by STAFF")
    void shouldAllowStaffToReadLeadTime() throws Exception {
        when(reorderSettingsService.getSettings()).thenReturn(new ReorderSettingsResponse(
                14,
                OffsetDateTime.parse("2026-11-01T09:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/settings/reorder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leadTimeDays").value(14))
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PUT /api/v1/settings/reorder should save the owner's lead time")
    void shouldAllowOwnerToUpdateLeadTime() throws Exception {
        when(reorderSettingsService.updateLeadTimeDays(eq(21), any())).thenReturn(
                new ReorderSettingsResponse(21, OffsetDateTime.parse("2026-11-01T09:05:00Z"))
        );

        mockMvc.perform(put("/api/v1/settings/reorder")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadTimeDays": 21}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.leadTimeDays").value(21));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("PUT /api/v1/settings/reorder should be forbidden to STAFF")
    void shouldDenyStaffFromUpdatingLeadTime() throws Exception {
        mockMvc.perform(put("/api/v1/settings/reorder")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadTimeDays": 21}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PUT /api/v1/settings/reorder should reject a lead time outside one to three hundred and sixty-five days")
    void shouldRejectInvalidLeadTime() throws Exception {
        mockMvc.perform(put("/api/v1/settings/reorder")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadTimeDays": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("leadTimeDays"));
    }
}
