package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.common.error.InvalidReturnQuantityException;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import com.shvmpk.stock_ledger.order.controller.OrderController;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.CsvImportResult;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.OrderStatusSummaryResponse;
import com.shvmpk.stock_ledger.order.dto.ReturnResponse;
import com.shvmpk.stock_ledger.order.entity.ItemCondition;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.service.OrderCsvImportService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("OrderController Web Slice Tests (Channel 1 Manual Entry & Channel 2 CSV)")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderCsvImportService csvImportService;

    @MockitoBean
    private com.shvmpk.stock_ledger.order.service.OrderReturnService returnService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Valid manual order creation should return 201 Created with standard envelope")
    void shouldCreateManualOrderSuccessfully() throws Exception {
        String validJson = """
                {
                  "customerName": "Astrid Lindqvist",
                  "customerEmail": "astrid@scandiboutique.se",
                  "shippingAddress": "Kungsgatan 44, 111 35 Stockholm, Sweden",
                  "customerShippingCharge": 4.99,
                  "items": [
                    {
                      "sku": "HOOD-NVY-XL",
                      "quantity": 2,
                      "unitPrice": 60.00
                    }
                  ]
                }
                """;

        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId,
                "ORD-2026-0042",
                "MANUAL",
                OrderStatus.PENDING,
                "Astrid Lindqvist",
                "astrid@scandiboutique.se",
                "Kungsgatan 44, 111 35 Stockholm, Sweden",
                new BigDecimal("4.99"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new OrderResponse.OrderItemResponse(UUID.randomUUID(), "HOOD-NVY-XL", 2, new BigDecimal("60.00"))),
                OffsetDateTime.now()
        );

        when(orderService.createManualOrder(any(CreateOrderRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-2026-0042"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].sku").value("HOOD-NVY-XL"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Manual order with invalid payload should return 400 Bad Request with RFC 7807 problem details")
    void shouldRejectInvalidOrderPayload() throws Exception {
        String invalidJson = """
                {
                  "customerName": "",
                  "customerEmail": "invalid-email-format",
                  "shippingAddress": "",
                  "customerShippingCharge": -5.00,
                  "items": []
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams").isArray());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Insufficient stock should return 409 Conflict with INSUFFICIENT_STOCK problem details")
    void shouldReturnConflictWhenStockInsufficient() throws Exception {
        String conflictJson = """
                {
                  "customerName": "Astrid Lindqvist",
                  "customerEmail": "astrid@scandiboutique.se",
                  "shippingAddress": "Kungsgatan 44, 111 35 Stockholm, Sweden",
                  "customerShippingCharge": 4.99,
                  "items": [
                    {
                      "sku": "HOOD-NVY-XL",
                      "quantity": 10,
                      "unitPrice": 60.00
                    }
                  ]
                }
                """;

        when(orderService.createManualOrder(any(CreateOrderRequest.class)))
                .thenThrow(new InsufficientStockException("HOOD-NVY-XL", 10, 2));

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.detail").value("Cannot reserve 10 units of SKU 'HOOD-NVY-XL'. Only 2 available."));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("CSV import endpoint should accept file and return 200 OK with import summary")
    void shouldImportOrdersFromCsvSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "orders_october_batch.csv",
                "text/csv",
                "order_number,customer_name,customer_email,shipping_address,customer_shipping_charge,sku,quantity,unit_price\nORD-001,Astrid,a@b.se,Address,4.99,HOOD-NVY-XL,1,60.00".getBytes()
        );

        CsvImportResult mockResult = new CsvImportResult(
                1,
                1,
                0,
                0,
                List.of()
        );

        when(csvImportService.importOrdersFromCsv(any())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/v1/orders/import-csv")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.importedOrders").value(1))
                .andExpect(jsonPath("$.data.failedRows").value(0));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Pack order endpoint should return 200 OK with READY_TO_SHIP status")
    void shouldPackOrderSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, "ORD-2026-0042", "MANUAL", OrderStatus.READY_TO_SHIP,
                "Astrid Lindqvist", "astrid@scandiboutique.se", "Kungsgatan 44, Stockholm",
                new BigDecimal("4.99"), null, null, null, null, null, null, null, List.of(), OffsetDateTime.now()
        );

        when(orderService.packOrder(orderId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/pack")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_TO_SHIP"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Ship order endpoint should return 200 OK with SHIPPED status and carrier tracking details")
    void shouldShipOrderSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        String shipPayload = """
                {
                  "carrier": "DHL",
                  "parcelSize": "MEDIUM",
                  "trackingNumber": "1234567890"
                }
                """;

        OrderResponse response = new OrderResponse(
                orderId, "ORD-2026-0042", "MANUAL", OrderStatus.SHIPPED,
                "Astrid Lindqvist", "astrid@scandiboutique.se", "Kungsgatan 44, Stockholm",
                new BigDecimal("4.99"), new BigDecimal("6.50"), "DHL", "MEDIUM", "1234567890",
                "https://www.dhl.com/en/express/tracking.html?AWB=1234567890", null, null, List.of(), OffsetDateTime.now()
        );

        when(orderService.shipOrder(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/ship")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shipPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.carrier").value("DHL"))
                .andExpect(jsonPath("$.data.trackingNumber").value("1234567890"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Cancel order endpoint should return 200 OK with CANCELLED status")
    void shouldCancelOrderSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, "ORD-2026-0042", "MANUAL", OrderStatus.CANCELLED,
                "Astrid Lindqvist", "astrid@scandiboutique.se", "Kungsgatan 44, Stockholm",
                new BigDecimal("4.99"), null, null, null, null, null, null, null, List.of(), OffsetDateTime.now()
        );

        when(orderService.cancelOrder(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .with(csrf())
                        .param("reason", "Customer requested cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Process return endpoint should return 200 OK with return details")
    void shouldProcessReturnSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        String returnPayload = String.format("""
                {
                  "reason": "Size too small",
                  "items": [
                    {
                      "orderItemId": "%s",
                      "quantity": 1,
                      "condition": "SELLABLE"
                    }
                  ]
                }
                """, orderItemId);

        com.shvmpk.stock_ledger.order.dto.ReturnResponse response = new com.shvmpk.stock_ledger.order.dto.ReturnResponse(
                returnId,
                orderId,
                "Size too small",
                List.of(new com.shvmpk.stock_ledger.order.dto.ReturnResponse.ReturnItemResponse(
                        UUID.randomUUID(),
                        orderItemId,
                        "HOOD-NVY-XL",
                        1,
                        com.shvmpk.stock_ledger.order.entity.ItemCondition.SELLABLE,
                        true
                )),
                OffsetDateTime.now()
        );

        when(returnService.processReturn(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/returns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].restocked").value(true))
                .andExpect(jsonPath("$.data.items[0].condition").value("SELLABLE"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/orders/{id} should return 200 OK with order details")
    void shouldGetOrderByIdSuccessfully() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, "ORD-2026-0042", "MANUAL", OrderStatus.PENDING,
                "Astrid Lindqvist", "astrid@scandiboutique.se", "Kungsgatan 44, Stockholm",
                new BigDecimal("4.99"), null, null, null, null, null, null, null, List.of(), OffsetDateTime.now()
        );

        when(orderService.getOrderById(orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-2026-0042"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/orders should return 200 OK with paginated orders")
    void shouldListOrdersWithPagination() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderResponse response = new OrderResponse(
                orderId, "ORD-2026-0042", "MANUAL", OrderStatus.PENDING,
                "Astrid Lindqvist", "astrid@scandiboutique.se", "Kungsgatan 44, Stockholm",
                new BigDecimal("4.99"), null, null, null, null, null, null, null, List.of(), OffsetDateTime.now()
        );

        org.springframework.data.domain.Page<OrderResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response), org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(orderService.listOrders(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/orders?status=PENDING&page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/orders/summary should return status counts")
    void shouldGetOrderStatusSummary() throws Exception {
        when(orderService.getStatusSummary()).thenReturn(List.of(
                new OrderStatusSummaryResponse(OrderStatus.PENDING, 4),
                new OrderStatusSummaryResponse(OrderStatus.WAITING_FOR_STOCK, 2)
        ));

        mockMvc.perform(get("/api/v1/orders/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].count").value(4))
                .andExpect(jsonPath("$.data[1].status").value("WAITING_FOR_STOCK"))
                .andExpect(jsonPath("$.data[1].count").value(2));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("GET /api/v1/orders/{id}/returns should return return history")
    void shouldGetReturnHistory() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();

        ReturnResponse response = new ReturnResponse(
                returnId,
                orderId,
                "Hoodie arrived with a loose seam",
                "Quality team inspected the seam and approved restocking.",
                new ReturnResponse.ProcessedByResponse(
                        operatorId,
                        "Freja Lindholm",
                        "freja.lindholm@nordicliving.se"
                ),
                List.of(new ReturnResponse.ReturnItemResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "HOOD-NVY-XL",
                        1,
                        ItemCondition.SELLABLE,
                        true
                )),
                OffsetDateTime.now()
        );

        when(returnService.getReturnsForOrder(orderId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].operatorNotes")
                        .value("Quality team inspected the seam and approved restocking."))
                .andExpect(jsonPath("$.data[0].processedBy.fullName").value("Freja Lindholm"))
                .andExpect(jsonPath("$.data[0].processedBy.email")
                        .value("freja.lindholm@nordicliving.se"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Over-return should return 400 with INVALID_RETURN_QUANTITY problem details")
    void shouldReturnBadRequestForOverReturn() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = String.format("""
                {
                  "reason": "Customer shipped back too many units",
                  "operatorNotes": "Return intake flagged an over-return.",
                  "items": [
                    {
                      "orderItemId": "%s",
                      "quantity": 3,
                      "condition": "SELLABLE"
                    }
                  ]
                }
                """, UUID.randomUUID());

        when(returnService.processReturn(any(), any(), any()))
                .thenThrow(new InvalidReturnQuantityException("HOOD-NVY-XL", 3, 2));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/returns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RETURN_QUANTITY"))
                .andExpect(jsonPath("$.detail")
                        .value("Cannot return 3 units of SKU 'HOOD-NVY-XL'. Only 2 remaining unreturned units are available."));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Return operator notes longer than 2000 characters should return 400")
    void shouldRejectOversizedOperatorNotes() throws Exception {
        UUID orderId = UUID.randomUUID();
        String payload = String.format("""
                {
                  "reason": "Customer return",
                  "operatorNotes": "%s",
                  "items": [
                    {
                      "orderItemId": "%s",
                      "quantity": 1,
                      "condition": "SELLABLE"
                    }
                  ]
                }
                """, "x".repeat(2001), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/returns")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("operatorNotes"));
    }
}
