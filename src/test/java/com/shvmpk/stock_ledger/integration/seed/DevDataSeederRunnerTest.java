package com.shvmpk.stock_ledger.integration.seed;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.config.seed.DevDataSeederRunner;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.repository.OrderReturnRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
// Test-only key; the dev profile does not load application-test.yaml.
@TestPropertySource(properties = {
        "app.platform.credentials-key=bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04="
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("DevDataSeederRunner Lifecycle Integration Test")
class DevDataSeederRunnerTest {

    @Autowired(required = false)
    private DevDataSeederRunner devDataSeederRunner;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private OrderReturnRepository orderReturnRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ShippingPriceListRepository shippingPriceListRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        orderReturnRepository.deleteAll();
        orderRepository.deleteAll();
        platformConnectionRepository.deleteAll();
        stockMovementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productVariantRepository.deleteAll();
        productRepository.deleteAll();
        shippingPriceListRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Dev profile should load DevDataSeederRunner and auto-populate seed data")
    void shouldAutoSeedOnDevProfileStartup() {
        assertThat(devDataSeederRunner).isNotNull();
        assertThat(productRepository.count()).isGreaterThanOrEqualTo(30);
    }
}
