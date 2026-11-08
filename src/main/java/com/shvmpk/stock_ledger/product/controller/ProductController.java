package com.shvmpk.stock_ledger.product.controller;

import com.shvmpk.stock_ledger.common.api.ApiResponse;
import com.shvmpk.stock_ledger.config.security.CurrentUserResolver;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.product.dto.LowStockVariantResponse;
import com.shvmpk.stock_ledger.product.dto.ProductCatalogResponse;
import com.shvmpk.stock_ledger.product.dto.SaveProductRequest;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.service.ProductCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class ProductController {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductCatalogService productCatalogService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductCatalogResponse>>> listProducts(
            @RequestParam(value = "includeRetired", defaultValue = "false") boolean includeRetired,
            Authentication authentication
    ) {
        List<ProductCatalogResponse> response = productCatalogService.list(
                canViewCost(authentication),
                includeRetired
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> getProduct(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        ProductCatalogResponse response = productCatalogService.get(id, canViewCost(authentication));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> createProduct(
            @Valid @RequestBody SaveProductRequest request
    ) {
        ProductCatalogResponse response = productCatalogService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/products/" + response.id()))
                .body(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody SaveProductRequest request
    ) {
        ProductCatalogResponse response = productCatalogService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> retireProduct(
            @PathVariable UUID id
    ) {
        ProductCatalogResponse response = productCatalogService.retireProduct(
                id,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> restoreProduct(
            @PathVariable UUID id
    ) {
        ProductCatalogResponse response = productCatalogService.restoreProduct(
                id,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{productId}/variants/{variantId}/retire")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> retireVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId
    ) {
        ProductCatalogResponse response = productCatalogService.retireVariant(
                productId,
                variantId,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{productId}/variants/{variantId}/restore")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<ProductCatalogResponse>> restoreVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId
    ) {
        ProductCatalogResponse response = productCatalogService.restoreVariant(
                productId,
                variantId,
                currentUserResolver.currentUserIdOrNull()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/low-stock")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<LowStockVariantResponse>>> listLowStockVariants() {
        List<Product> products = productRepository.findAllByActiveTrue();
        Map<UUID, InventoryLevel> inventoryMap = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(inv -> inv.getProductVariant().getId(), inv -> inv, (a, b) -> a));

        List<LowStockVariantResponse> lowStock = new ArrayList<>();
        for (Product product : products) {
            for (ProductVariant variant : product.getVariants()) {
                if (!variant.isActive()) {
                    continue;
                }
                Integer minStockThreshold = variant.getMinStockThreshold();
                if (minStockThreshold == null) {
                    continue;
                }

                InventoryLevel inv = inventoryMap.get(variant.getId());
                int onHand = inv != null ? inv.getOnHandQuantity() : 0;
                int reserved = inv != null ? inv.getReservedQuantity() : 0;
                int available = inv != null ? inv.getAvailableQuantity() : 0;

                if (available <= minStockThreshold) {
                    lowStock.add(new LowStockVariantResponse(
                            variant.getId(),
                            product.getName(),
                            variant.getSku(),
                            variant.getBarcode(),
                            variant.getOptionValue(),
                            onHand,
                            reserved,
                            available,
                            minStockThreshold
                    ));
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(lowStock));
    }

    private static boolean canViewCost(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_OWNER"::equals);
    }
}
