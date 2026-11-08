package com.shvmpk.stock_ledger.integration.web;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Current User Resolver & Actor Attribution Integration Tests")
class CurrentUserResolverIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should attribute an inbound stock receipt to the logged-in warehouse user")
    void shouldAttributeStockReceiptToLoggedInUser() throws Exception {
        User staff = User.create(
                UUID.randomUUID(),
                "mira.lind@northwind-supply.co",
                passwordEncoder.encode("WarehousePass2026!"),
                "Mira Lind",
                Role.STAFF
        );
        userRepository.save(staff);

        Product product = Product.create(
                UUID.randomUUID(),
                "Organic Heavyweight Hoodie",
                "Loopback terry with a brushed interior",
                "Apparel"
        );
        ProductVariant variant = product.addVariant(
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
        productRepository.save(product);
        inventoryRepository.save(InventoryLevel.initialize(UUID.randomUUID(), variant, 40));

        String loginPayload = """
                {
                    "email": "mira.lind@northwind-supply.co",
                    "password": "WarehousePass2026!"
                }
                """;

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        String receivePayload = """
                {
                    "productVariantId": "%s",
                    "quantity": 18,
                    "notes": "PO-2026-10 inbound receipt from Northwind Textiles"
                }
                """.formatted(variant.getId());

        mockMvc.perform(post("/api/v1/stock/receive")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receivePayload))
                .andExpect(status().isOk());

        List<StockMovement> movements = stockMovementRepository
                .findAllByProductVariantIdOrderByCreatedAtDesc(variant.getId());

        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getReason()).isEqualTo(StockMovementReason.RECEIVE);
        assertThat(movements.get(0).getActorId()).isEqualTo(staff.getId());
    }
}
