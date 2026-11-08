package com.shvmpk.stock_ledger.product.service;

import com.shvmpk.stock_ledger.common.error.ProductValidationException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.product.dto.ProductCatalogResponse;
import com.shvmpk.stock_ledger.product.dto.ProductVariantStockResponse;
import com.shvmpk.stock_ledger.product.dto.SaveProductRequest;
import com.shvmpk.stock_ledger.product.dto.SaveProductVariantRequest;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductCatalogEventRecorder eventRecorder;

    @Transactional(readOnly = true)
    public List<ProductCatalogResponse> list(boolean canViewCost, boolean includeRetired) {
        return productRepository.findAll().stream()
                .filter(product -> includeRetired || product.isActive())
                .map(product -> mapToResponse(product, canViewCost, includeRetired))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductCatalogResponse get(UUID productId, boolean canViewCost) {
        return mapToResponse(requireProduct(productId), canViewCost, true);
    }

    @Transactional
    public ProductCatalogResponse create(SaveProductRequest request) {
        rejectClientSuppliedVariantIds(request);
        rejectDuplicateSkusInRequest(request);
        rejectSkusAlreadyInUse(request, null);

        Product product = Product.create(
                UUID.randomUUID(),
                request.name(),
                request.description(),
                request.category()
        );
        for (SaveProductVariantRequest variantRequest : request.variants()) {
            product.addVariant(
                    UUID.randomUUID(),
                    variantRequest.sku(),
                    variantRequest.barcode(),
                    variantRequest.optionName(),
                    variantRequest.optionValue(),
                    variantRequest.costPrice(),
                    variantRequest.sellingPrice(),
                    variantRequest.weightGrams(),
                    variantRequest.minStockThreshold()
            );
        }

        Product saved = productRepository.save(product);
        createZeroStockRecords(saved.getVariants());
        return mapToResponse(saved, true, true);
    }

    @Transactional
    public ProductCatalogResponse update(UUID productId, SaveProductRequest request) {
        Product product = requireProduct(productId);
        rejectDuplicateSkusInRequest(request);
        rejectForeignVariantIds(product, request);
        rejectSkusAlreadyInUse(request, productId);

        product.updateDetails(request.name(), request.description(), request.category());

        Map<UUID, ProductVariant> existingVariants = product.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<ProductVariant> addedVariants = new ArrayList<>();

        for (SaveProductVariantRequest variantRequest : request.variants()) {
            if (variantRequest.id() == null) {
                addedVariants.add(product.addVariant(
                        UUID.randomUUID(),
                        variantRequest.sku(),
                        variantRequest.barcode(),
                        variantRequest.optionName(),
                        variantRequest.optionValue(),
                        variantRequest.costPrice(),
                        variantRequest.sellingPrice(),
                        variantRequest.weightGrams(),
                        variantRequest.minStockThreshold()
                ));
            } else {
                existingVariants.get(variantRequest.id()).updateDetails(
                        variantRequest.sku(),
                        variantRequest.barcode(),
                        variantRequest.optionName(),
                        variantRequest.optionValue(),
                        variantRequest.costPrice(),
                        variantRequest.sellingPrice(),
                        variantRequest.weightGrams(),
                        variantRequest.minStockThreshold()
                );
            }
        }

        Product saved = productRepository.save(product);
        createZeroStockRecords(addedVariants);
        return mapToResponse(saved, true, true);
    }

    @Transactional
    public ProductCatalogResponse retireProduct(UUID productId, UUID actorId) {
        Product product = requireProduct(productId);
        if (product.isActive()) {
            product.retire();
            eventRecorder.recordRetired(product, null, actorId);
        }
        return mapToResponse(product, true, true);
    }

    @Transactional
    public ProductCatalogResponse restoreProduct(UUID productId, UUID actorId) {
        Product product = requireProduct(productId);
        if (!product.isActive()) {
            product.restore();
            eventRecorder.recordRestored(product, null, actorId);
        }
        return mapToResponse(product, true, true);
    }

    @Transactional
    public ProductCatalogResponse retireVariant(UUID productId, UUID variantId, UUID actorId) {
        Product product = requireProduct(productId);
        ProductVariant variant = requireVariant(product, variantId);
        if (variant.isActive()) {
            variant.retire();
            eventRecorder.recordRetired(product, variant, actorId);
        }
        return mapToResponse(product, true, true);
    }

    @Transactional
    public ProductCatalogResponse restoreVariant(UUID productId, UUID variantId, UUID actorId) {
        Product product = requireProduct(productId);
        ProductVariant variant = requireVariant(product, variantId);
        if (!variant.isActive()) {
            variant.restore();
            eventRecorder.recordRestored(product, variant, actorId);
        }
        return mapToResponse(product, true, true);
    }

    private Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private ProductVariant requireVariant(Product product, UUID variantId) {
        return product.getVariants().stream()
                .filter(variant -> variant.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductVariant", variantId));
    }

    private void createZeroStockRecords(List<ProductVariant> variants) {
        for (ProductVariant variant : variants) {
            inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), variant, 0));
        }
    }

    private void rejectClientSuppliedVariantIds(SaveProductRequest request) {
        for (int index = 0; index < request.variants().size(); index++) {
            if (request.variants().get(index).id() != null) {
                throw new ProductValidationException(
                        "variants[%d].id".formatted(index),
                        "A new product cannot specify variant ids."
                );
            }
        }
    }

    private void rejectDuplicateSkusInRequest(SaveProductRequest request) {
        Map<String, Integer> firstIndexBySku = new HashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            String sku = normalizeSku(request.variants().get(index).sku());
            Integer firstIndex = firstIndexBySku.putIfAbsent(sku, index);
            if (firstIndex != null) {
                throw new ProductValidationException(
                        "variants[%d].sku".formatted(index),
                        "SKU '%s' appears more than once in this product.".formatted(sku)
                );
            }
        }
    }

    private void rejectSkusAlreadyInUse(SaveProductRequest request, UUID productId) {
        for (int index = 0; index < request.variants().size(); index++) {
            SaveProductVariantRequest variantRequest = request.variants().get(index);
            String sku = normalizeSku(variantRequest.sku());
            final int variantIndex = index;
            productVariantRepository.findBySkuIgnoreCase(sku).ifPresent(existing -> {
                boolean sameVariant = productId != null
                        && variantRequest.id() != null
                        && existing.getId().equals(variantRequest.id())
                        && existing.getProduct().getId().equals(productId);
                if (!sameVariant) {
                    throw new ProductValidationException(
                            "variants[%d].sku".formatted(variantIndex),
                            "SKU '%s' is already in use. Choose a different SKU."
                                    .formatted(existing.getSku())
                    );
                }
            });
        }
    }

    private void rejectForeignVariantIds(Product product, SaveProductRequest request) {
        Set<UUID> productVariantIds = product.getVariants().stream()
                .map(ProductVariant::getId)
                .collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        for (int index = 0; index < request.variants().size(); index++) {
            UUID variantId = request.variants().get(index).id();
            if (variantId == null) {
                continue;
            }
            if (!productVariantIds.contains(variantId)) {
                throw new ProductValidationException(
                        "variants[%d].id".formatted(index),
                        "Variant does not belong to this product."
                );
            }
            if (!seen.add(variantId)) {
                throw new ProductValidationException(
                        "variants[%d].id".formatted(index),
                        "Variant appears more than once in this product."
                );
            }
        }
    }

    private ProductCatalogResponse mapToResponse(
            Product product,
            boolean canViewCost,
            boolean includeRetiredVariants
    ) {
        List<UUID> variantIds = product.getVariants().stream()
                .map(ProductVariant::getId)
                .toList();
        Map<UUID, InventoryLevel> inventoryByVariant = variantIds.isEmpty()
                ? Map.of()
                : inventoryRepository.findAllByProductVariantIdIn(variantIds).stream()
                        .collect(Collectors.toMap(
                                inventory -> inventory.getProductVariant().getId(),
                                Function.identity(),
                                (first, second) -> first
                        ));

        List<ProductVariantStockResponse> variants = product.getVariants().stream()
                .filter(variant -> includeRetiredVariants || variant.isActive())
                .map(variant -> {
                    InventoryLevel inventory = inventoryByVariant.get(variant.getId());
                    int onHand = inventory != null ? inventory.getOnHandQuantity() : 0;
                    int reserved = inventory != null ? inventory.getReservedQuantity() : 0;
                    int available = inventory != null ? inventory.getAvailableQuantity() : 0;
                    Integer minStockThreshold = variant.getMinStockThreshold();
                    return new ProductVariantStockResponse(
                            variant.getId(),
                            variant.getSku(),
                            variant.getBarcode(),
                            variant.getOptionName(),
                            variant.getOptionValue(),
                            variant.isActive(),
                            canViewCost ? variant.getCostPrice() : null,
                            variant.getSellingPrice(),
                            variant.getWeightGrams(),
                            minStockThreshold,
                            onHand,
                            reserved,
                            available,
                            minStockThreshold != null && available <= minStockThreshold
                    );
                })
                .toList();

        return new ProductCatalogResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.isActive(),
                variants
        );
    }

    private static String normalizeSku(String sku) {
        return sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT);
    }
}
