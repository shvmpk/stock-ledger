package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.DuplicateStockCountItemException;
import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.inventory.controller.StockCountController;
import com.shvmpk.stock_ledger.inventory.dto.RecordStockCountItemRequest;
import com.shvmpk.stock_ledger.inventory.dto.StockCountItemResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountOperatorResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountResponse;
import com.shvmpk.stock_ledger.inventory.dto.StockCountSummaryResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockCountStatus;
import com.shvmpk.stock_ledger.inventory.service.StockCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockCountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("StockCountController Web Slice Tests (Sessions, Validation & RBAC)")
class StockCountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockCountService stockCountService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    private UUID operatorId;
    private UUID sessionId;
    private StockCountOperatorResponse operator;

    @BeforeEach
    void setUp() {
        operatorId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        operator = new StockCountOperatorResponse(
                operatorId,
                "Freja Lindholm",
                "freja.lindholm@nordicliving.se"
        );
        when(currentUserResolver.currentUserIdOrNull()).thenReturn(operatorId);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/stock/counts should start a session for STAFF")
    void shouldStartSessionForStaff() throws Exception {
        StockCountResponse response = new StockCountResponse(
                sessionId,
                StockCountStatus.IN_PROGRESS,
                operator,
                OffsetDateTime.now(),
                null,
                0,
                0,
                false,
                0,
                List.of()
        );
        when(stockCountService.start(operatorId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/stock/counts").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.conductedBy.id").value(operatorId.toString()))
                .andExpect(jsonPath("$.data.conductedBy.fullName").value("Freja Lindholm"))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.completedAt").doesNotExist())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    @DisplayName("POST /api/v1/stock/counts should start a session for OWNER")
    void shouldStartSessionForOwner() throws Exception {
        StockCountResponse response = new StockCountResponse(
                sessionId,
                StockCountStatus.IN_PROGRESS,
                operator,
                OffsetDateTime.now(),
                null,
                0,
                0,
                false,
                0,
                List.of()
        );
        when(stockCountService.start(operatorId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/stock/counts").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST /api/v1/stock/counts/{id}/items should record a counted item")
    void shouldRecordStockCountItem() throws Exception {
        StockCountItemResponse item = new StockCountItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "Heavyweight Cotton Hoodie",
                "Color / Size",
                "Navy / XL",
                12,
                9,
                -3,
                "Cycle count CC-2026-014",
                false,
                0
        );
        StockCountResponse response = new StockCountResponse(
                sessionId,
                StockCountStatus.IN_PROGRESS,
                operator,
                OffsetDateTime.now(),
                null,
                1,
                -3,
                false,
                0,
                List.of(item)
        );
        when(stockCountService.recordItem(eq(sessionId), any(RecordStockCountItemRequest.class), eq(operatorId)))
                .thenReturn(response);

        String payload = """
                {
                  "productVariantId": "%s",
                  "countedOnHand": 9,
                  "notes": "Cycle count CC-2026-014"
                }
                """.formatted(item.productVariantId());

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data.items[0].expectedOnHand").value(12))
                .andExpect(jsonPath("$.data.items[0].countedOnHand").value(9))
                .andExpect(jsonPath("$.data.items[0].variance").value(-3))
                .andExpect(jsonPath("$.data.items[0].hasReservationDeficit").value(false))
                .andExpect(jsonPath("$.data.itemCount").value(1))
                .andExpect(jsonPath("$.data.totalVariance").value(-3));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Negative countedOnHand should return 400 VALIDATION_FAILED")
    void shouldRejectNegativeCountedOnHand() throws Exception {
        String payload = """
                {
                  "productVariantId": "%s",
                  "countedOnHand": -1,
                  "notes": "Invalid negative count"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("countedOnHand"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Missing productVariantId should return 400 VALIDATION_FAILED")
    void shouldRejectMissingVariant() throws Exception {
        String payload = """
                {
                  "countedOnHand": 9,
                  "notes": "Missing variant"
                }
                """;

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("productVariantId"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Duplicate variant should return 400 DUPLICATE_STOCK_COUNT_ITEM")
    void shouldReturnDuplicateProblemDetail() throws Exception {
        when(stockCountService.recordItem(eq(sessionId), any(RecordStockCountItemRequest.class), eq(operatorId)))
                .thenThrow(new DuplicateStockCountItemException("HOOD-NVY-XL"));

        String payload = """
                {
                  "productVariantId": "%s",
                  "countedOnHand": 7,
                  "notes": "Accidental rescan"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_STOCK_COUNT_ITEM"))
                .andExpect(jsonPath("$.title").value("Duplicate Stock Count Item"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("POST complete and cancel should return the updated session")
    void shouldCompleteAndCancelSession() throws Exception {
        StockCountResponse completed = new StockCountResponse(
                sessionId,
                StockCountStatus.COMPLETED,
                operator,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1,
                -3,
                false,
                0,
                List.of()
        );
        when(stockCountService.complete(sessionId)).thenReturn(completed);

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/complete").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        StockCountResponse cancelled = new StockCountResponse(
                sessionId,
                StockCountStatus.CANCELLED,
                operator,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                1,
                -3,
                false,
                0,
                List.of()
        );
        when(stockCountService.cancel(sessionId)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/stock/counts/" + sessionId + "/cancel").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/stock/counts/{id} should return the session and items")
    void shouldGetSession() throws Exception {
        StockCountItemResponse item = new StockCountItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HOOD-NVY-XL",
                "Heavyweight Cotton Hoodie",
                "Color / Size",
                "Navy / XL",
                12,
                9,
                -3,
                "Cycle count CC-2026-014",
                true,
                1
        );
        StockCountResponse response = new StockCountResponse(
                sessionId,
                StockCountStatus.IN_PROGRESS,
                operator,
                OffsetDateTime.now(),
                null,
                1,
                -3,
                true,
                1,
                List.of(item)
        );
        when(stockCountService.get(sessionId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/stock/counts/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.items[0].sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.data.items[0].reservationDeficit").value(1))
                .andExpect(jsonPath("$.data.hasReservationDeficit").value(true));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/stock/counts should paginate sessions and filter by status")
    void shouldListSessions() throws Exception {
        StockCountSummaryResponse summary = new StockCountSummaryResponse(
                sessionId,
                StockCountStatus.COMPLETED,
                operator,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                2,
                -4,
                true,
                2
        );
        when(stockCountService.list(eq(StockCountStatus.COMPLETED), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(summary), pageable, 1);
                });

        mockMvc.perform(get("/api/v1/stock/counts")
                        .param("status", "COMPLETED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data[0].itemCount").value(2))
                .andExpect(jsonPath("$.data[0].totalVariance").value(-4))
                .andExpect(jsonPath("$.data[0].hasReservationDeficit").value(true))
                .andExpect(jsonPath("$.pagination.page").value(0))
                .andExpect(jsonPath("$.pagination.size").value(20))
                .andExpect(jsonPath("$.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(1))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("List should clamp size and reject a negative page")
    void shouldClampSizeAndRejectNegativePage() throws Exception {
        StockCountSummaryResponse summary = new StockCountSummaryResponse(
                sessionId,
                StockCountStatus.IN_PROGRESS,
                operator,
                OffsetDateTime.now(),
                null,
                0,
                0,
                false,
                0
        );
        when(stockCountService.list(any(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(summary), pageable, 1);
                });

        mockMvc.perform(get("/api/v1/stock/counts").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(100));

        mockMvc.perform(get("/api/v1/stock/counts").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.size").value(1));

        mockMvc.perform(get("/api/v1/stock/counts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("page"));
    }

    @Test
    @DisplayName("Unauthenticated stock count requests should return 401")
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/stock/counts").with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/stock/counts"))
                .andExpect(status().isUnauthorized());
    }
}
