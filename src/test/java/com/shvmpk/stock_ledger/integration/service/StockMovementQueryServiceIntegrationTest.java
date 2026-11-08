package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.dto.StockMovementResponse;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.service.StockMovementQueryService;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Stock Movement Query Service Integration Tests")
class StockMovementQueryServiceIntegrationTest {

    @Autowired
    private StockMovementQueryService queryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User warehouseUser;
    private ProductVariant hoodieVariant;
    private ProductVariant sockVariant;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM stock_movements");

        warehouseUser = User.create(
                UUID.randomUUID(),
                "mira.lind@northwind-supply.co",
                "$2a$10$warehousehash",
                "Mira Lind",
                Role.STAFF
        );
        userRepository.saveAndFlush(warehouseUser);

        Product hoodie = Product.create(
                UUID.randomUUID(),
                "Organic Heavyweight Hoodie",
                "Loopback terry with a brushed interior",
                "Apparel"
        );
        hoodieVariant = hoodie.addVariant(
                UUID.randomUUID(),
                "HOOD-NVY-M",
                "5060123456789",
                "Size / Color",
                "M / Navy",
                new BigDecimal("24.00"),
                new BigDecimal("68.00"),
                620,
                18
        );
        productRepository.saveAndFlush(hoodie);

        Product socks = Product.create(
                UUID.randomUUID(),
                "Merino Wool Crew Sock",
                "Fine-gauge merino with a reinforced heel",
                "Apparel"
        );
        sockVariant = socks.addVariant(
                UUID.randomUUID(),
                "SOCK-MRN-L",
                "5060123456799",
                "Size",
                "L",
                new BigDecimal("6.50"),
                new BigDecimal("18.00"),
                80,
                12
        );
        productRepository.saveAndFlush(socks);

        OffsetDateTime now = OffsetDateTime.now();
        insertMovement(
                hoodieVariant,
                warehouseUser,
                StockMovementReason.RECEIVE,
                "MANUAL_RESTOCK",
                24,
                0,
                "PO-2026-09 inbound receipt",
                now.minusDays(3)
        );
        insertMovement(
                hoodieVariant,
                null,
                StockMovementReason.ORDER_RESERVED,
                "ORDER",
                0,
                2,
                "Order ORD-2026-1042 reserved",
                now.minusDays(2)
        );
        insertMovement(
                sockVariant,
                warehouseUser,
                StockMovementReason.RETURN_RESTOCK,
                "RETURN",
                2,
                0,
                "Return RTN-2026-0088 restocked",
                now.minusDays(1)
        );
    }

    @Test
    @DisplayName("Should return newest-first pages with product and actor names")
    void shouldReturnNewestFirstPagesWithProductAndActorNames() {
        Page<StockMovementResponse> firstPage = queryService.search(null, null, null, null, 0, 2);

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent())
                .extracting(StockMovementResponse::reason)
                .containsExactly(StockMovementReason.RETURN_RESTOCK, StockMovementReason.ORDER_RESERVED);

        StockMovementResponse newest = firstPage.getContent().get(0);
        assertThat(newest.productName()).isEqualTo("Merino Wool Crew Sock");
        assertThat(newest.actorName()).isEqualTo("Mira Lind");

        StockMovementResponse second = firstPage.getContent().get(1);
        assertThat(second.productName()).isEqualTo("Organic Heavyweight Hoodie");
        assertThat(second.actorName()).isNull();
    }

    @Test
    @DisplayName("Should filter by SKU case-insensitively and treat blank SKU as no filter")
    void shouldFilterBySku() {
        Page<StockMovementResponse> hoodieMovements =
                queryService.search("hood-nvy-m", null, null, null, 0, 20);
        assertThat(hoodieMovements.getTotalElements()).isEqualTo(2);
        assertThat(hoodieMovements.getContent())
                .extracting(StockMovementResponse::sku)
                .containsOnly("HOOD-NVY-M");

        Page<StockMovementResponse> allMovements =
                queryService.search("  ", null, null, null, 0, 20);
        assertThat(allMovements.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should filter by reason and by an inclusive from/to window")
    void shouldFilterByReasonAndDateWindow() {
        Page<StockMovementResponse> receives =
                queryService.search(null, StockMovementReason.RECEIVE, null, null, 0, 20);
        assertThat(receives.getTotalElements()).isEqualTo(1);
        assertThat(receives.getContent().get(0).reason()).isEqualTo(StockMovementReason.RECEIVE);

        OffsetDateTime now = OffsetDateTime.now();
        Page<StockMovementResponse> recentReturns = queryService.search(
                null,
                null,
                now.minusDays(1).minusHours(1),
                now.minusHours(1),
                0,
                20
        );
        assertThat(recentReturns.getTotalElements()).isEqualTo(1);
        assertThat(recentReturns.getContent().get(0).reason()).isEqualTo(StockMovementReason.RETURN_RESTOCK);
    }

    @Test
    @DisplayName("Should return the requested page and clamp invalid paging parameters")
    void shouldPaginateAndClampParameters() {
        Page<StockMovementResponse> secondPage = queryService.search(null, null, null, null, 1, 2);

        assertThat(secondPage.getNumber()).isEqualTo(1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent().get(0).reason()).isEqualTo(StockMovementReason.RECEIVE);

        Page<StockMovementResponse> clamped = queryService.search(null, null, null, null, -4, 500);
        assertThat(clamped.getNumber()).isZero();
        assertThat(clamped.getSize()).isEqualTo(100);
    }

    private void insertMovement(
            ProductVariant variant,
            User actor,
            StockMovementReason reason,
            String referenceType,
            int deltaOnHand,
            int deltaReserved,
            String notes,
            OffsetDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO stock_movements
                    (id, product_variant_id, sku, product_name, delta_on_hand, delta_reserved, reason,
                     reference_type, reference_id, actor_id, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                variant.getId(),
                variant.getSku(),
                variant.getProduct().getName(),
                deltaOnHand,
                deltaReserved,
                reason.name(),
                referenceType,
                UUID.randomUUID(),
                actor != null ? actor.getId() : null,
                notes,
                createdAt
        );
    }
}
