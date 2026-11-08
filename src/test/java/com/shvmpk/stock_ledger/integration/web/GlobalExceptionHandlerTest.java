package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.common.error.DomainException;
import com.shvmpk.stock_ledger.common.error.GlobalExceptionHandler;
import com.shvmpk.stock_ledger.common.error.InsufficientReservedStockException;
import com.shvmpk.stock_ledger.common.error.InsufficientStockException;
import com.shvmpk.stock_ledger.common.error.InvalidReturnQuantityException;
import com.shvmpk.stock_ledger.common.error.ReservationHolder;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.RetiredProductException;
import com.shvmpk.stock_ledger.common.error.ShippingPriceNotConfiguredException;
import com.shvmpk.stock_ledger.common.error.StockAction;
import com.shvmpk.stock_ledger.common.error.StockShortageDetails;
import com.shvmpk.stock_ledger.config.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ExceptionProbeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ExceptionProbeController.class})
@DisplayName("Global Exception Handler & RFC 7807 Problem Details Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    record SampleRequest(
            @NotBlank(message = "SKU cannot be blank")
            String sku,

            @Min(value = 1, message = "Quantity must be at least 1")
            int quantity
    ) {}

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Validation failure should return RFC 7807 400 Bad Request with field-level invalidParams")
    void shouldReturnRfc7807OnValidationFailure() throws Exception {
        String invalidJson = """
                {
                  "sku": "",
                  "quantity": 0
                }
                """;

        mockMvc.perform(post("/api/v1/probe/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams", hasSize(2)));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("InsufficientStockException should return RFC 7807 409 Conflict with INSUFFICIENT_STOCK code")
    void shouldReturnRfc7807OnInsufficientStock() throws Exception {
        mockMvc.perform(get("/api/v1/probe/insufficient-stock"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Insufficient Stock"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.detail").value("Cannot reserve 5 units of SKU 'HOOD-NVY-XL'. Only 2 available."));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("ResourceNotFoundException should return RFC 7807 404 Not Found")
    void shouldReturnRfc7807OnResourceNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Malformed JSON should return RFC 7807 400 VALIDATION_FAILED")
    void shouldReturnRfc7807OnMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/probe/malformed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams").isEmpty());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Type mismatch should return RFC 7807 400 VALIDATION_FAILED")
    void shouldReturnRfc7807OnTypeMismatch() throws Exception {
        mockMvc.perform(get("/api/v1/probe/type-mismatch").param("quantity", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("quantity"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Missing request parameter should return RFC 7807 400 VALIDATION_FAILED")
    void shouldReturnRfc7807OnMissingParameter() throws Exception {
        mockMvc.perform(get("/api/v1/probe/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.invalidParams[0].field").value("sku"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Constraint violation should return RFC 7807 400 VALIDATION_FAILED")
    void shouldReturnRfc7807OnConstraintViolation() throws Exception {
        mockMvc.perform(get("/api/v1/probe/constraint").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Oversized upload should return RFC 7807 413 PAYLOAD_TOO_LARGE")
    void shouldReturnRfc7807OnOversizedUpload() throws Exception {
        mockMvc.perform(get("/api/v1/probe/upload-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("ShippingPriceNotConfiguredException should return RFC 7807 400")
    void shouldReturnRfc7807OnMissingShippingPrice() throws Exception {
        mockMvc.perform(get("/api/v1/probe/shipping-price-missing"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Shipping Price Not Configured"))
                .andExpect(jsonPath("$.code").value("SHIPPING_PRICE_NOT_CONFIGURED"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("InvalidReturnQuantityException should return RFC 7807 400")
    void shouldReturnRfc7807OnInvalidReturnQuantity() throws Exception {
        mockMvc.perform(get("/api/v1/probe/invalid-return"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Return Quantity"))
                .andExpect(jsonPath("$.code").value("INVALID_RETURN_QUANTITY"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Enriched insufficient-stock problem carries figures, reservation holders, and actions")
    void shouldExposeStockShortageExtensions() throws Exception {
        mockMvc.perform(get("/api/v1/probe/insufficient-stock-details"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.requestedQuantity").value(5))
                .andExpect(jsonPath("$.availableQuantity").value(2))
                .andExpect(jsonPath("$.onHandQuantity").value(8))
                .andExpect(jsonPath("$.reservedQuantity").value(6))
                .andExpect(jsonPath("$.reservedBy", hasSize(2)))
                .andExpect(jsonPath("$.reservedBy[0].orderNumber").value("ORD-2026-1042"))
                .andExpect(jsonPath("$.reservedBy[0].reservedQuantity").value(4))
                .andExpect(jsonPath("$.reservedBy[1].orderNumber").value("ORD-2026-1043"))
                .andExpect(jsonPath("$.actions", hasSize(4)))
                .andExpect(jsonPath("$.actions[0].code").value("RECEIVE_STOCK"))
                .andExpect(jsonPath("$.actions[1].code").value("REDUCE_QUANTITY"))
                .andExpect(jsonPath("$.actions[2].code").value("VIEW_MOVEMENTS"))
                .andExpect(jsonPath("$.actions[3].code").value("VIEW_RESERVATIONS"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Enriched insufficient-reserved problem points at reservations and movements")
    void shouldExposeReservedStockExtensions() throws Exception {
        mockMvc.perform(get("/api/v1/probe/insufficient-reserved-details"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_RESERVED_STOCK"))
                .andExpect(jsonPath("$.sku").value("HOOD-NVY-XL"))
                .andExpect(jsonPath("$.requestedQuantity").value(5))
                .andExpect(jsonPath("$.availableQuantity").value(2))
                .andExpect(jsonPath("$.onHandQuantity").value(8))
                .andExpect(jsonPath("$.reservedQuantity").value(6))
                .andExpect(jsonPath("$.reservedBy", hasSize(2)))
                .andExpect(jsonPath("$.actions", hasSize(2)))
                .andExpect(jsonPath("$.actions[0].code").value("VIEW_RESERVATIONS"))
                .andExpect(jsonPath("$.actions[1].code").value("VIEW_MOVEMENTS"))
                .andExpect(jsonPath("$.detail", containsString("Only 6 are reserved")));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("Retired product problem links to the retired-products list")
    void shouldExposeRetiredProductAction() throws Exception {
        mockMvc.perform(get("/api/v1/probe/retired"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_RETIRED"))
                .andExpect(jsonPath("$.sku").value("JKT-WAX-OLV-M"))
                .andExpect(jsonPath("$.actions", hasSize(1)))
                .andExpect(jsonPath("$.actions[0].code").value("VIEW_RETIRED_PRODUCTS"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    @DisplayName("InsufficientReservedStockException should return RFC 7807 409")
    void shouldReturnRfc7807OnInsufficientReservedStock() throws Exception {
        mockMvc.perform(get("/api/v1/probe/insufficient-reserved"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Insufficient Reserved Stock"))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_RESERVED_STOCK"));
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    @Validated
    static class ExceptionProbeController {

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody SampleRequest request) {
            return "valid";
        }

        @PostMapping("/malformed")
        public String malformed(@RequestBody SampleRequest request) {
            return "valid";
        }

        @GetMapping("/type-mismatch")
        public String typeMismatch(@RequestParam int quantity) {
            return "valid";
        }

        @GetMapping("/missing-param")
        public String missingParam(@RequestParam String sku) {
            return "valid";
        }

        @GetMapping("/constraint")
        public String constraint(@RequestParam @Min(1) int quantity) {
            return "valid";
        }

        @GetMapping("/upload-too-large")
        public String uploadTooLarge() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/insufficient-stock")
        public String insufficientStock() {
            throw new InsufficientStockException("HOOD-NVY-XL", 5, 2);
        }

        @GetMapping("/insufficient-stock-details")
        public String insufficientStockDetails() {
            throw new InsufficientStockException(shortageDetails(
                    List.of(
                            StockAction.RECEIVE_STOCK,
                            StockAction.REDUCE_QUANTITY,
                            StockAction.VIEW_MOVEMENTS,
                            StockAction.VIEW_RESERVATIONS
                    )
            ));
        }

        @GetMapping("/insufficient-reserved-details")
        public String insufficientReservedDetails() {
            throw new InsufficientReservedStockException("ship", shortageDetails(
                    List.of(StockAction.VIEW_RESERVATIONS, StockAction.VIEW_MOVEMENTS)
            ));
        }

        @GetMapping("/retired")
        public String retired() {
            throw new RetiredProductException("JKT-WAX-OLV-M", "ordered");
        }

        private static StockShortageDetails shortageDetails(List<StockAction> actions) {
            return new StockShortageDetails(
                    "HOOD-NVY-XL",
                    5,
                    2,
                    8,
                    6,
                    List.of(
                            new ReservationHolder(
                                    UUID.fromString("00000000-0000-4000-8000-000000001042"),
                                    "ORD-2026-1042",
                                    4,
                                    OffsetDateTime.parse("2026-10-29T10:00:00Z")
                            ),
                            new ReservationHolder(
                                    UUID.fromString("00000000-0000-4000-8000-000000001043"),
                                    "ORD-2026-1043",
                                    2,
                                    OffsetDateTime.parse("2026-10-30T11:30:00Z")
                            )
                    ),
                    actions
            );
        }

        @GetMapping("/not-found")
        public String notFound() {
            throw new ResourceNotFoundException("Order", "ORD-1234");
        }

        @GetMapping("/shipping-price-missing")
        public String shippingPriceMissing() {
            throw new ShippingPriceNotConfiguredException("DHL", "MEDIUM");
        }

        @GetMapping("/invalid-return")
        public String invalidReturn() {
            throw new InvalidReturnQuantityException("HOOD-NVY-RET", 3, 2);
        }

        @GetMapping("/insufficient-reserved")
        public String insufficientReserved() {
            throw new InsufficientReservedStockException("HOOD-NVY-LC", 2);
        }
    }
}
