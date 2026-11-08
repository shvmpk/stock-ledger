package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.platform.controller.PlatformConnectionController;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionEventQueryService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionTestService;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformConnectionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Platform Catalogue Web Slice & RBAC Tests")
class PlatformCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlatformConnectionService platformConnectionService;

    @MockitoBean
    private PlatformConnectionEventQueryService platformConnectionEventQueryService;

    @MockitoBean
    private PlatformSyncService platformSyncService;

    @MockitoBean
    private PlatformConnectionTestService platformConnectionTestService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/catalog should describe every channel the server understands")
    void shouldReturnThePlatformCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data[0].displayName").value("Shopify"))
                .andExpect(jsonPath("$.data[0].supported").value(true))
                .andExpect(jsonPath("$.data[0].outboundSync").value(true))
                .andExpect(jsonPath("$.data[0].webhookPath").value("/api/v1/webhooks/orders/shopify"))
                .andExpect(jsonPath("$.data[0].apiEndpointExample").value("https://your-store.myshopify.com/admin/api/2026-10"))
                .andExpect(jsonPath("$.data[0].credentialFields[0].key").value("accessToken"))
                .andExpect(jsonPath("$.data[0].credentialFields[0].label").value("Access token"))
                .andExpect(jsonPath("$.data[0].signatureHeaders", hasItem("X-Shopify-Hmac-Sha256")))
                .andExpect(jsonPath("$.data[1].platformType").value("WOOCOMMERCE"))
                .andExpect(jsonPath("$.data[1].apiEndpointExample").value("https://your-store.com/wp-json/wc/v3"))
                .andExpect(jsonPath("$.data[1].credentialFields.length()").value(2))
                .andExpect(jsonPath("$.data[1].credentialFields[1].key").value("consumerSecret"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/catalog should list Etsy as unsupported with a plain reason")
    void shouldExplainWhyEtsyCannotBeConnected() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[2].platformType").value("ETSY"))
                .andExpect(jsonPath("$.data[2].supported").value(false))
                .andExpect(jsonPath("$.data[2].outboundSync").value(false))
                .andExpect(jsonPath("$.data[2].unsupportedReason").isNotEmpty())
                .andExpect(jsonPath("$.data[2].credentialFields.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/catalog should describe credential fields without carrying values")
    void shouldDescribeCredentialFieldsWithoutCarryingValues() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].webhookSecret").doesNotExist())
                .andExpect(jsonPath("$.data[0].apiCredentials").doesNotExist())
                .andExpect(jsonPath("$.data[0].credentialFields[0].secret").value(true))
                .andExpect(jsonPath("$.data[0].credentialFields[0].value").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/platforms/catalog should return 403 Forbidden when caller is STAFF")
    void shouldForbidStaffFromReadingTheCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/catalog"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/platforms/catalog should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedCatalogueRead() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/catalog"))
                .andExpect(status().isUnauthorized());
    }
}
