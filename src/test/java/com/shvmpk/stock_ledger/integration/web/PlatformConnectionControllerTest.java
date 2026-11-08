package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.ConcurrentChangeException;
import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.common.error.NoPlatformChangesException;
import com.shvmpk.stock_ledger.common.error.PlatformWebhookAddressInUseException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.platform.controller.PlatformConnectionController;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionEventResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionTestResponse;
import com.shvmpk.stock_ledger.platform.dto.PlatformSyncResponse;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformTestOutcome;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionCreationResult;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionEventQueryService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionTestService;
import com.shvmpk.stock_ledger.platform.service.PlatformSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlatformConnectionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("PlatformConnectionController Web Slice & RBAC Tests")
class PlatformConnectionControllerTest {

    private static final String SHOPIFY_ENDPOINT = "https://northwind-supply.myshopify.com";
    private static final String SHOPIFY_WEBHOOK_SECRET = "dummy-webhook-secret";
    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String PAUSE_REASON = "Store closed for a seasonal break.";

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
    @DisplayName("POST /api/v1/platforms should create a connection and return 201 with Location")
    void shouldCreatePlatformConnectionAsOwner() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "5d1c8a4f-2e9b-4a7d-8c3f-6b1e9d2a5c84";
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionService.create(any(), eq(ownerId), eq(idempotencyKey)))
                .thenReturn(new PlatformConnectionCreationResult(
                        response(connectionId, PlatformType.SHOPIFY, true),
                        false
                ));

        mockMvc.perform(post("/api/v1/platforms")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformType": "SHOPIFY",
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "%s",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/platforms/" + connectionId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data.apiEndpoint").value(SHOPIFY_ENDPOINT))
                .andExpect(jsonPath("$.data.webhookPath").value("/api/v1/webhooks/orders/shopify"))
                .andExpect(jsonPath("$.data.syncStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.syncSupported").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.webhookConfigured").value(true))
                .andExpect(jsonPath("$.data.credentialsConfigured").value(true))
                .andExpect(jsonPath("$.data.webhookSecret").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.consumerKey").doesNotExist())
                .andExpect(jsonPath("$.data.consumerSecret").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedApiCredentials").doesNotExist());

        verify(platformConnectionService).create(any(), eq(ownerId), eq(idempotencyKey));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms should return 200 without a Location when the request key was already used")
    void shouldReturnTheOriginalConnectionWhenTheCreateIsReplayed() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String idempotencyKey = "5d1c8a4f-2e9b-4a7d-8c3f-6b1e9d2a5c84";
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionService.create(any(), eq(ownerId), eq(idempotencyKey)))
                .thenReturn(new PlatformConnectionCreationResult(
                        response(connectionId, PlatformType.SHOPIFY, true),
                        true
                ));

        mockMvc.perform(post("/api/v1/platforms")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformType": "SHOPIFY",
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "%s",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms should list connections and honor the active filter")
    void shouldListPlatformConnectionsAsOwner() throws Exception {
        PlatformConnectionResponse shopify = response(UUID.randomUUID(), PlatformType.SHOPIFY, true);
        PlatformConnectionResponse woo = response(UUID.randomUUID(), PlatformType.WOOCOMMERCE, false);
        when(platformConnectionService.list(null)).thenReturn(List.of(shopify, woo));
        when(platformConnectionService.list(true)).thenReturn(List.of(shopify));

        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data[1].platformType").value("WOOCOMMERCE"))
                .andExpect(jsonPath("$.data[0].webhookSecret").doesNotExist())
                .andExpect(jsonPath("$.data[1].accessToken").doesNotExist());

        mockMvc.perform(get("/api/v1/platforms").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].active").value(true));

        verify(platformConnectionService).list(null);
        verify(platformConnectionService).list(true);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/{id} should return a single connection")
    void shouldGetPlatformConnectionAsOwner() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(platformConnectionService.get(connectionId))
                .thenReturn(response(connectionId, PlatformType.SHOPIFY, true));

        mockMvc.perform(get("/api/v1/platforms/{id}", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data.credentialsConfigured").value(true))
                .andExpect(jsonPath("$.data.webhookSecret").doesNotExist());

        verify(platformConnectionService).get(connectionId);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should update and reactivate a connection")
    void shouldUpdatePlatformConnectionAsOwner() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionService.update(eq(connectionId), any(), eq(ownerId)))
                .thenReturn(response(connectionId, PlatformType.SHOPIFY, true));

        mockMvc.perform(patch("/api/v1/platforms/{id}", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "apiEndpoint": "https://northwind-supply.myshopify.com/admin",
                                  "webhookSecret": "whsec_new_7a2c9e4f1b6d8305",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.active").value(true));

        verify(platformConnectionService).update(eq(connectionId), any(), eq(ownerId));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should pause a connection with a reason")
    void shouldPausePlatformConnectionWithReason() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionService.update(eq(connectionId), any(), eq(ownerId)))
                .thenReturn(response(connectionId, PlatformType.SHOPIFY, false, PAUSE_REASON));

        mockMvc.perform(patch("/api/v1/platforms/{id}", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "active": false,
                                  "pauseReason": "%s"
                                }
                                """.formatted(PAUSE_REASON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.pauseReason").value(PAUSE_REASON))
                .andExpect(jsonPath("$.data.deactivatedAt").isNotEmpty());

        verify(platformConnectionService).update(eq(connectionId), any(), eq(ownerId));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("DELETE /api/v1/platforms/{id} should deactivate and return the resource")
    void shouldDeactivatePlatformConnectionAsOwner() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionService.deactivate(connectionId, PAUSE_REASON, ownerId))
                .thenReturn(response(connectionId, PlatformType.SHOPIFY, false, PAUSE_REASON));

        mockMvc.perform(delete("/api/v1/platforms/{id}", connectionId).param("reason", PAUSE_REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.deactivatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.pauseReason").value(PAUSE_REASON))
                .andExpect(jsonPath("$.data.webhookSecret").doesNotExist());

        verify(platformConnectionService).deactivate(connectionId, PAUSE_REASON, ownerId);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Platform endpoints should return 403 for STAFF")
    void shouldForbidStaffFromPlatformEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PERMISSIONS"));

        mockMvc.perform(post("/api/v1/platforms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformType": "SHOPIFY",
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "%s",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @DisplayName("Platform endpoints should return 401 for anonymous callers")
    void shouldRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/v1/platforms"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/platforms/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms should reject invalid request bodies")
    void shouldValidateCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/platforms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "short",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("platformType")))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("webhookSecret")));

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms should reject a webhook address that is not URL-safe")
    void shouldRejectUnsafeWebhookAddress() throws Exception {
        mockMvc.perform(post("/api/v1/platforms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformType": "SHOPIFY",
                                  "displayName": "Northwind Supply",
                                  "slug": "Northwind Supply / EU",
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "%s",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("slug")));

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should return 400 when no fields are supplied")
    void shouldRejectEmptyUpdate() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(platformConnectionService.update(eq(connectionId), any(), any()))
                .thenThrow(new NoPlatformChangesException());

        mockMvc.perform(patch("/api/v1/platforms/{id}", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_PLATFORM_CHANGES"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should reject a blank display name")
    void shouldRejectBlankDisplayName() throws Exception {
        mockMvc.perform(patch("/api/v1/platforms/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 0,
                                  "displayName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("displayName")));

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should reject an edit without a version")
    void shouldRejectAnEditWithoutAVersion() throws Exception {
        mockMvc.perform(patch("/api/v1/platforms/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Northwind Supply EU"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[*].field", hasItem("version")));

        verifyNoInteractions(platformConnectionService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("PATCH /api/v1/platforms/{id} should return 409 when the connection moved on")
    void shouldRejectAStaleEdit() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(platformConnectionService.update(eq(connectionId), any(), any()))
                .thenThrow(new ConcurrentChangeException());

        mockMvc.perform(patch("/api/v1/platforms/{id}", connectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": 3,
                                  "displayName": "Northwind Supply EU"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_CHANGE"))
                .andExpect(jsonPath("$.detail", containsString("changed by someone else")));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/{id} should return 404 for an unknown connection")
    void shouldReturnNotFoundForUnknownConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(platformConnectionService.get(connectionId))
                .thenThrow(new ResourceNotFoundException("PlatformConnection", connectionId));

        mockMvc.perform(get("/api/v1/platforms/{id}", connectionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/{id}/events should return the connection history")
    void shouldReturnConnectionHistory() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PlatformConnectionEventResponse event = new PlatformConnectionEventResponse(
                eventId,
                connectionId,
                "SHOPIFY",
                PlatformConnectionChangeType.PAUSED,
                actorId,
                "elin.sandberg@northwind-supply.co",
                List.of("active"),
                "Store closed for a seasonal break.",
                null,
                null,
                OffsetDateTime.parse("2026-10-28T09:15:30Z")
        );
        when(platformConnectionEventQueryService.history(eq(connectionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/platforms/{id}/events", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$.data[0].platformConnectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data[0].platformType").value("SHOPIFY"))
                .andExpect(jsonPath("$.data[0].changeType").value("PAUSED"))
                .andExpect(jsonPath("$.data[0].actorUserId").value(actorId.toString()))
                .andExpect(jsonPath("$.data[0].actorEmail").value("elin.sandberg@northwind-supply.co"))
                .andExpect(jsonPath("$.data[0].fields[0]").value("active"))
                .andExpect(jsonPath("$.data[0].reason").value(PAUSE_REASON))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("GET /api/v1/platforms/{id}/events should return 404 for an unknown connection")
    void shouldReturnNotFoundForUnknownConnectionHistory() throws Exception {
        UUID connectionId = UUID.randomUUID();
        when(platformConnectionEventQueryService.history(eq(connectionId), any(Pageable.class)))
                .thenThrow(new ResourceNotFoundException("PlatformConnection", connectionId));

        mockMvc.perform(get("/api/v1/platforms/{id}/events", connectionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/platforms/{id}/events should forbid STAFF")
    void shouldForbidStaffFromReadingConnectionHistory() throws Exception {
        mockMvc.perform(get("/api/v1/platforms/{id}/events", UUID.randomUUID()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformConnectionEventQueryService);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms should return 409 when the webhook address is already in use")
    void shouldReturnConflictWhenTheWebhookAddressIsInUse() throws Exception {
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(UUID.randomUUID());
        when(platformConnectionService.create(any(), any(), any()))
                .thenThrow(new PlatformWebhookAddressInUseException("shopify"));

        mockMvc.perform(post("/api/v1/platforms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "platformType": "SHOPIFY",
                                  "apiEndpoint": "%s",
                                  "webhookSecret": "%s",
                                  "accessToken": "%s"
                                }
                                """.formatted(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLATFORM_WEBHOOK_ADDRESS_IN_USE"))
                .andExpect(jsonPath("$.detail").value(containsString(
                        "/api/v1/webhooks/orders/shopify"
                )));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms/{id}/sync should return the final sync status when caller is OWNER")
    void shouldAllowOwnerToTriggerSync() throws Exception {
        UUID connectionId = UUID.randomUUID();
        OffsetDateTime lastSyncAt = OffsetDateTime.parse("2026-10-22T12:00:00Z");
        when(platformSyncService.syncConnection(connectionId))
                .thenReturn(new PlatformSyncResponse(connectionId, "SUCCESS", lastSyncAt, null));

        mockMvc.perform(post("/api/v1/platforms/{id}/sync", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.lastSyncAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lastErrorMessage").doesNotExist());

        verify(platformSyncService).syncConnection(connectionId);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms/{id}/sync should return the final FAILED status with a safe error")
    void shouldReturnFailedFinalSyncStatus() throws Exception {
        UUID connectionId = UUID.randomUUID();
        OffsetDateTime lastSyncAt = OffsetDateTime.parse("2026-10-22T12:00:00Z");
        when(platformSyncService.syncConnection(connectionId))
                .thenReturn(new PlatformSyncResponse(
                        connectionId,
                        "FAILED",
                        lastSyncAt,
                        "Rate limit exceeded (HTTP 429). Retry after backoff."
                ));

        mockMvc.perform(post("/api/v1/platforms/{id}/sync", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.lastErrorMessage").value(
                        "Rate limit exceeded (HTTP 429). Retry after backoff."
                ));

        verify(platformSyncService).syncConnection(connectionId);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/platforms/{id}/sync should return 403 Forbidden when caller is STAFF")
    void shouldForbidStaffFromTriggeringSync() throws Exception {
        UUID connectionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/platforms/{id}/sync", connectionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/platforms/{id}/sync should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedSync() throws Exception {
        UUID connectionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/platforms/{id}/sync", connectionId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/platforms/{id}/test should return the visible test result when caller is OWNER")
    void shouldAllowOwnerToTestAConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OffsetDateTime testedAt = OffsetDateTime.parse("2026-10-28T14:05:00Z");
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(ownerId);
        when(platformConnectionTestService.test(connectionId, ownerId))
                .thenReturn(new PlatformConnectionTestResponse(
                        connectionId,
                        PlatformTestOutcome.AUTHENTICATION_FAILED,
                        401,
                        "The channel rejected the credentials (HTTP 401). Check the token and save it again.",
                        testedAt,
                        null
                ));

        mockMvc.perform(post("/api/v1/platforms/{id}/test", connectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.connectionId").value(connectionId.toString()))
                .andExpect(jsonPath("$.data.outcome").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.data.httpStatus").value(401))
                .andExpect(jsonPath("$.data.message").value(containsString("HTTP 401")))
                .andExpect(jsonPath("$.data.testedAt").isNotEmpty());

        verify(platformConnectionTestService).test(connectionId, ownerId);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/platforms/{id}/test should return 403 Forbidden when caller is STAFF")
    void shouldForbidStaffFromTestingAConnection() throws Exception {
        UUID connectionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/platforms/{id}/test", connectionId))
                .andExpect(status().isForbidden());

        verifyNoInteractions(platformConnectionTestService);
    }

    @Test
    @DisplayName("POST /api/v1/platforms/{id}/test should return 401 Unauthorized when unauthenticated")
    void shouldRejectUnauthenticatedConnectionTest() throws Exception {
        UUID connectionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/platforms/{id}/test", connectionId))
                .andExpect(status().isUnauthorized());
    }

    private PlatformConnectionResponse response(UUID id, PlatformType type, boolean active) {
        return response(id, type, active, active ? null : PAUSE_REASON);
    }

    private PlatformConnectionResponse response(
            UUID id,
            PlatformType type,
            boolean active,
            String pauseReason
    ) {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-10-22T10:15:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-10-22T10:15:00Z");
        return new PlatformConnectionResponse(
                id,
                0L,
                type,
                type.displayName(),
                type.defaultSlug(),
                type == PlatformType.SHOPIFY ? SHOPIFY_ENDPOINT : "https://northwind-home.co.uk",
                type.webhookPath(),
                "PENDING",
                type.supportsOutboundSync(),
                null,
                null,
                active,
                true,
                true,
                createdAt,
                updatedAt,
                active ? null : OffsetDateTime.parse("2026-10-22T11:00:00Z"),
                pauseReason,
                null,
                null,
                null,
                null,
                null
        );
    }
}
