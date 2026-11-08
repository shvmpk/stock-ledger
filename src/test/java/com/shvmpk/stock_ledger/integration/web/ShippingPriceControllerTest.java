package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.shipping.controller.ShippingPriceController;
import com.shvmpk.stock_ledger.shipping.dto.ShippingRateDto;
import com.shvmpk.stock_ledger.shipping.dto.UpdateShippingRateRequest;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.service.ShippingPricingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShippingPriceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ShippingPriceController Web Slice & RBAC Tests")
class ShippingPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShippingPricingService pricingService;

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/shipping/rates should be accessible by STAFF and return rate list")
    void shouldAllowStaffToViewRates() throws Exception {
        ShippingRateDto rate = new ShippingRateDto(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("6.50"),
                true
        );

        when(pricingService.getAllRates()).thenReturn(List.of(rate));

        mockMvc.perform(get("/api/v1/shipping/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].carrier").value("DHL"))
                .andExpect(jsonPath("$.data[0].cost").value(6.50));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PUT /api/v1/shipping/rates should be allowed for OWNER")
    void shouldAllowOwnerToConfigureRate() throws Exception {
        String payload = """
                {
                  "carrier": "DHL",
                  "parcelSize": "MEDIUM",
                  "cost": 7.20,
                  "active": true
                }
                """;

        ShippingRateDto updated = new ShippingRateDto(
                UUID.randomUUID(),
                Carrier.DHL,
                ParcelSize.MEDIUM,
                new BigDecimal("7.20"),
                true
        );

        when(pricingService.configureRate(any(UpdateShippingRateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/shipping/rates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cost").value(7.20));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("PUT /api/v1/shipping/rates should return 403 Forbidden for STAFF")
    void shouldDenyStaffFromConfiguringRate() throws Exception {
        String payload = """
                {
                  "carrier": "DHL",
                  "parcelSize": "MEDIUM",
                  "cost": 7.20,
                  "active": true
                }
                """;

        mockMvc.perform(put("/api/v1/shipping/rates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }
}
