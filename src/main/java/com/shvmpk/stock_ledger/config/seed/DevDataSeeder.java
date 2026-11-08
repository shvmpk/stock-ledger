package com.shvmpk.stock_ledger.config.seed;

import com.shvmpk.stock_ledger.inventory.entity.InventoryLevel;
import com.shvmpk.stock_ledger.inventory.entity.StockMovement;
import com.shvmpk.stock_ledger.inventory.entity.StockMovementReason;
import com.shvmpk.stock_ledger.inventory.repository.InventoryRepository;
import com.shvmpk.stock_ledger.inventory.repository.StockMovementRepository;
import com.shvmpk.stock_ledger.inventory.service.InventoryService;
import com.shvmpk.stock_ledger.order.dto.CreateOrderItemRequest;
import com.shvmpk.stock_ledger.order.dto.CreateOrderRequest;
import com.shvmpk.stock_ledger.order.dto.OrderResponse;
import com.shvmpk.stock_ledger.order.dto.ProcessReturnRequest;
import com.shvmpk.stock_ledger.order.dto.ReturnResponse;
import com.shvmpk.stock_ledger.order.dto.ShipOrderRequest;
import com.shvmpk.stock_ledger.order.entity.ItemCondition;
import com.shvmpk.stock_ledger.order.entity.Order;
import com.shvmpk.stock_ledger.order.entity.OrderItem;
import com.shvmpk.stock_ledger.order.entity.OrderSource;
import com.shvmpk.stock_ledger.order.entity.OrderStatus;
import com.shvmpk.stock_ledger.order.repository.OrderRepository;
import com.shvmpk.stock_ledger.order.service.OrderReturnService;
import com.shvmpk.stock_ledger.order.service.OrderService;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.product.entity.Product;
import com.shvmpk.stock_ledger.product.entity.ProductVariant;
import com.shvmpk.stock_ledger.product.repository.ProductRepository;
import com.shvmpk.stock_ledger.product.repository.ProductVariantRepository;
import com.shvmpk.stock_ledger.shipping.entity.Carrier;
import com.shvmpk.stock_ledger.shipping.entity.ParcelSize;
import com.shvmpk.stock_ledger.shipping.entity.ShippingPriceRate;
import com.shvmpk.stock_ledger.shipping.repository.ShippingPriceListRepository;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * High-fidelity development data seeder.
 * Seeds 30 authentic Nordic/UK retail products with 80+ variants,
 * inventory levels, historical orders across all statuses, and carrier rates.
 * Idempotent: safe to run multiple times.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataSeeder {

    private static final String WEBHOOK_SECRET_AAD = "webhookSecret";
    private static final String API_CREDENTIALS_AAD = "apiCredentials";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OrderRepository orderRepository;
    private final ShippingPriceListRepository shippingPriceListRepository;
    private final PlatformConnectionRepository platformConnectionRepository;
    private final PlatformCredentialCipher platformCredentialCipher;
    private final PlatformCredentialsCodec platformCredentialsCodec;
    private final PasswordEncoder passwordEncoder;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final OrderReturnService orderReturnService;
    private final SeedDataBackdater seedDataBackdater;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public void seed() {
        transactionTemplate.executeWithoutResult(status -> seedCatalog());
        transactionTemplate.executeWithoutResult(status -> seedOrders());
        log.info("DevDataSeeder: seeded {} products, {} orders, and {} platform connections successfully",
                productRepository.count(), orderRepository.count(), platformConnectionRepository.count());
    }

    private void seedCatalog() {
        seedUsers();
        seedShippingRates();
        seedPlatformConnections();
        seedProductsAndVariants();
    }

    // ──────────────────────────────────────────────────────────────────
    // Users
    // ──────────────────────────────────────────────────────────────────
    private void seedUsers() {
        seedDemoUser("owner@stockledger.io", "OwnerPass2026!", "Ingrid Bergström", Role.OWNER);
        seedDemoUser("staff@stockledger.io", "StaffPass2026!", "Lars Eriksson", Role.STAFF);
    }

    /**
     * Demo credentials are part of the development contract: the documented
     * passwords and the "Use owner/staff demo" buttons must keep working after
     * any local password reset, so every dev startup restores them and clears
     * the forced-change flag.
     */
    private void seedDemoUser(String email, String rawPassword, String fullName, Role role) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(existing -> {
            boolean changed = false;
            if (!passwordEncoder.matches(rawPassword, existing.getPasswordHash())
                    || existing.isMustChangePassword()) {
                existing.changePassword(passwordEncoder.encode(rawPassword));
                changed = true;
            }
            if (!existing.getFullName().equals(fullName)) {
                existing.updateFullName(fullName);
                changed = true;
            }
            if (existing.getRole() != role) {
                existing.updateRole(role);
                changed = true;
            }
            if (!existing.isActive()) {
                existing.activate();
                changed = true;
            }
            if (changed) {
                userRepository.save(existing);
            }
        }, () -> userRepository.save(User.create(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(rawPassword),
                fullName,
                role)));
    }

    // ──────────────────────────────────────────────────────────────────
    // Platform Connections
    // ──────────────────────────────────────────────────────────────────
    private void seedPlatformConnections() {
        if (platformConnectionRepository.count() > 0) return;

        OffsetDateTime seededAt = OffsetDateTime.now();
        PlatformConnection shopify = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.SHOPIFY,
                "https://northwind-supply.myshopify.com/admin/api/2026-10",
                platformCredentialCipher.encrypt("dummy-webhook-secret", WEBHOOK_SECRET_AAD),
                platformCredentialCipher.encrypt(
                        platformCredentialsCodec.encode(new PlatformCredentials(
                                "dummy-shopify-access-token",
                                null,
                                null
                        )),
                        API_CREDENTIALS_AAD
                )
        );
        shopify.updateSyncStatus("SUCCESS", null);
        shopify.backdateLastSyncAt(seededAt.minusHours(2));
        platformConnectionRepository.save(shopify);

        PlatformConnection wooCommerce = PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.WOOCOMMERCE,
                "https://northwind-home.co.uk/wp-json/wc/v3",
                platformCredentialCipher.encrypt("whsec_4b8d1e7a2c9f6350", WEBHOOK_SECRET_AAD),
                platformCredentialCipher.encrypt(
                        platformCredentialsCodec.encode(new PlatformCredentials(
                                null,
                                "ck_8f2a6d1c4e9b7350a2f8d6c1",
                                "dummy-woocommerce-secret-2"
                        )),
                        API_CREDENTIALS_AAD
                )
        );
        wooCommerce.updateSyncStatus(
                "FAILED",
                "WooCommerce REST API returned HTTP 401: consumer key is invalid."
        );
        wooCommerce.backdateLastSyncAt(seededAt.minusDays(3));
        platformConnectionRepository.save(wooCommerce);
    }

    // ──────────────────────────────────────────────────────────────────
    // Shipping Rates
    // ──────────────────────────────────────────────────────────────────
    private void seedShippingRates() {
        if (shippingPriceListRepository.count() > 0) return;
        List<ShippingPriceRate> rates = List.of(
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.DHL,        ParcelSize.SMALL,  new BigDecimal("5.50")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.DHL,        ParcelSize.MEDIUM, new BigDecimal("7.20")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.DHL,        ParcelSize.LARGE,  new BigDecimal("10.90")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.FEDEX,      ParcelSize.SMALL,  new BigDecimal("6.00")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.FEDEX,      ParcelSize.MEDIUM, new BigDecimal("8.50")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.FEDEX,      ParcelSize.LARGE,  new BigDecimal("13.00")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.UPS,        ParcelSize.SMALL,  new BigDecimal("5.80")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.UPS,        ParcelSize.MEDIUM, new BigDecimal("7.90")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.UPS,        ParcelSize.LARGE,  new BigDecimal("12.50")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.ROYAL_MAIL, ParcelSize.SMALL,  new BigDecimal("3.95")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.ROYAL_MAIL, ParcelSize.MEDIUM, new BigDecimal("6.25")),
                ShippingPriceRate.create(UUID.randomUUID(), Carrier.ROYAL_MAIL, ParcelSize.LARGE,  new BigDecimal("9.75"))
        );
        shippingPriceListRepository.saveAll(rates);
    }

    // ──────────────────────────────────────────────────────────────────
    // Products & Variants
    // ──────────────────────────────────────────────────────────────────
    private List<ProductVariant> seedProductsAndVariants() {
        List<ProductVariant> allVariants = new ArrayList<>();
        allVariants.addAll(seedApparelProducts());
        allVariants.addAll(seedCoffeeProducts());
        allVariants.addAll(seedCoffeeAccessories());
        return allVariants;
    }

    private record VariantSpec(String sku, String barcode, String optName, String optValue,
                               BigDecimal cost, BigDecimal price, int weight, int minStock,
                               int initialStock) {}

    private List<ProductVariant> createAndSaveProduct(String name, String description, String category,
                                                       List<VariantSpec> specs) {
        if (productRepository.findByName(name).isPresent()) {
            return productRepository.findByName(name).get().getVariants();
        }
        Product product = Product.create(UUID.randomUUID(), name, description, category);
        List<ProductVariant> variants = new ArrayList<>();
        for (VariantSpec s : specs) {
            ProductVariant v = product.addVariant(UUID.randomUUID(), s.sku(), s.barcode(),
                    s.optName(), s.optValue(), s.cost(), s.price(), s.weight(), s.minStock());
            variants.add(v);
        }
        productRepository.save(product);

        // Create inventory levels and initial receiving movements
        for (int i = 0; i < variants.size(); i++) {
            ProductVariant v = variants.get(i);
            InventoryLevel inv = InventoryLevel.initialize(UUID.randomUUID(), v, specs.get(i).initialStock());
            inventoryRepository.save(inv);
            StockMovement movement = StockMovement.create(v, v.getSku(), v.getProduct().getName(), specs.get(i).initialStock(), 0,
                    StockMovementReason.RECEIVE, "RECEIVING", UUID.randomUUID(), null,
                    "Initial stock receipt from supplier");
            stockMovementRepository.save(movement);
        }
        return variants;
    }

    // 12 Apparel products, 3–4 variants each → ~42 variants
    private List<ProductVariant> seedApparelProducts() {
        List<ProductVariant> all = new ArrayList<>();
        all.addAll(createAndSaveProduct("Heavyweight Cotton Hoodie",
                "450 GSM organic loopback French terry, brushed interior",
                "Apparel",
                List.of(
                        new VariantSpec("HOOD-NVY-S",  "5060123456001", "Size / Color", "S / Navy",     new BigDecimal("24.00"), new BigDecimal("68.00"), 620, 5, 40),
                        new VariantSpec("HOOD-NVY-M",  "5060123456002", "Size / Color", "M / Navy",     new BigDecimal("24.00"), new BigDecimal("68.00"), 640, 5, 55),
                        new VariantSpec("HOOD-NVY-L",  "5060123456003", "Size / Color", "L / Navy",     new BigDecimal("24.00"), new BigDecimal("68.00"), 660, 5, 50),
                        new VariantSpec("HOOD-NVY-XL", "5060123456004", "Size / Color", "XL / Navy",    new BigDecimal("24.00"), new BigDecimal("68.00"), 680, 5, 35)
                )));
        all.addAll(createAndSaveProduct("Heavyweight Cotton Hoodie Stone",
                "450 GSM organic loopback French terry, brushed interior",
                "Apparel",
                List.of(
                        new VariantSpec("HOOD-STN-S",  "5060123456011", "Size / Color", "S / Stone",    new BigDecimal("24.00"), new BigDecimal("68.00"), 620, 5, 30),
                        new VariantSpec("HOOD-STN-M",  "5060123456012", "Size / Color", "M / Stone",    new BigDecimal("24.00"), new BigDecimal("68.00"), 640, 5, 45),
                        new VariantSpec("HOOD-STN-L",  "5060123456013", "Size / Color", "L / Stone",    new BigDecimal("24.00"), new BigDecimal("68.00"), 660, 5, 40)
                )));
        all.addAll(createAndSaveProduct("Relaxed Quarter-Zip Sweatshirt",
                "380 GSM enzyme-washed fleece, raglan sleeves",
                "Apparel",
                List.of(
                        new VariantSpec("QZ-OAT-S",  "5060123456021", "Size / Color", "S / Oatmeal",  new BigDecimal("19.50"), new BigDecimal("55.00"), 520, 5, 35),
                        new VariantSpec("QZ-OAT-M",  "5060123456022", "Size / Color", "M / Oatmeal",  new BigDecimal("19.50"), new BigDecimal("55.00"), 540, 5, 48),
                        new VariantSpec("QZ-OAT-L",  "5060123456023", "Size / Color", "L / Oatmeal",  new BigDecimal("19.50"), new BigDecimal("55.00"), 560, 5, 40),
                        new VariantSpec("QZ-OAT-XL", "5060123456024", "Size / Color", "XL / Oatmeal", new BigDecimal("19.50"), new BigDecimal("55.00"), 580, 5, 28)
                )));
        all.addAll(createAndSaveProduct("5-Panel Camp Cap",
                "100% washed twill, unstructured low-profile",
                "Accessories",
                List.of(
                        new VariantSpec("CAP-BLK-OS", "5060123456031", "Color", "Black",      new BigDecimal("8.00"), new BigDecimal("28.00"), 120, 10, 80),
                        new VariantSpec("CAP-OLV-OS", "5060123456032", "Color", "Olive",      new BigDecimal("8.00"), new BigDecimal("28.00"), 120, 10, 65),
                        new VariantSpec("CAP-NVY-OS", "5060123456033", "Color", "Navy",       new BigDecimal("8.00"), new BigDecimal("28.00"), 120, 10, 70)
                )));
        all.addAll(createAndSaveProduct("Organic Cotton T-Shirt",
                "180 GSM single-jersey GOTS certified, boxy fit",
                "Apparel",
                List.of(
                        new VariantSpec("TEE-WHT-S",  "5060123456041", "Size / Color", "S / White",  new BigDecimal("7.50"), new BigDecimal("28.00"), 200, 10, 60),
                        new VariantSpec("TEE-WHT-M",  "5060123456042", "Size / Color", "M / White",  new BigDecimal("7.50"), new BigDecimal("28.00"), 210, 10, 80),
                        new VariantSpec("TEE-WHT-L",  "5060123456043", "Size / Color", "L / White",  new BigDecimal("7.50"), new BigDecimal("28.00"), 220, 10, 70),
                        new VariantSpec("TEE-BLK-M",  "5060123456044", "Size / Color", "M / Black",  new BigDecimal("7.50"), new BigDecimal("28.00"), 210, 10, 75)
                )));
        all.addAll(createAndSaveProduct("Cargo Trousers",
                "Relaxed fit, 12oz organic cotton twill, 6 functional pockets",
                "Apparel",
                List.of(
                        new VariantSpec("CGO-STN-28", "5060123456051", "Size / Color", "28 / Stone",  new BigDecimal("22.00"), new BigDecimal("72.00"), 720, 5, 25),
                        new VariantSpec("CGO-STN-30", "5060123456052", "Size / Color", "30 / Stone",  new BigDecimal("22.00"), new BigDecimal("72.00"), 740, 5, 38),
                        new VariantSpec("CGO-STN-32", "5060123456053", "Size / Color", "32 / Stone",  new BigDecimal("22.00"), new BigDecimal("72.00"), 760, 5, 42)
                )));
        all.addAll(createAndSaveProduct("Ripstop Overshirt",
                "Waxed cotton ripstop, concealed chest pockets",
                "Apparel",
                List.of(
                        new VariantSpec("OVS-KHK-M",  "5060123456061", "Size / Color", "M / Khaki",  new BigDecimal("28.00"), new BigDecimal("89.00"), 580, 5, 20),
                        new VariantSpec("OVS-KHK-L",  "5060123456062", "Size / Color", "L / Khaki",  new BigDecimal("28.00"), new BigDecimal("89.00"), 600, 5, 28),
                        new VariantSpec("OVS-OLV-M",  "5060123456063", "Size / Color", "M / Olive",  new BigDecimal("28.00"), new BigDecimal("89.00"), 580, 5, 18)
                )));
        all.addAll(createAndSaveProduct("Merino Crew Neck Jumper",
                "100% extra-fine 17.5μm merino wool, rib-trim hem",
                "Apparel",
                List.of(
                        new VariantSpec("MRN-CRM-S",  "5060123456071", "Size / Color", "S / Cream",  new BigDecimal("32.00"), new BigDecimal("95.00"), 450, 5, 20),
                        new VariantSpec("MRN-CRM-M",  "5060123456072", "Size / Color", "M / Cream",  new BigDecimal("32.00"), new BigDecimal("95.00"), 470, 5, 28),
                        new VariantSpec("MRN-NVY-M",  "5060123456073", "Size / Color", "M / Navy",   new BigDecimal("32.00"), new BigDecimal("95.00"), 470, 5, 22)
                )));
        all.addAll(createAndSaveProduct("Tote Bag",
                "12oz heavyweight canvas, laminated interior",
                "Accessories",
                List.of(
                        new VariantSpec("TOT-NTL-OS", "5060123456081", "Color", "Natural", new BigDecimal("6.00"), new BigDecimal("22.00"), 250, 15, 100),
                        new VariantSpec("TOT-BLK-OS", "5060123456082", "Color", "Black",   new BigDecimal("6.00"), new BigDecimal("22.00"), 250, 15, 85)
                )));
        all.addAll(createAndSaveProduct("Beanies",
                "Soft-knit 100% merino, double-layered cuff",
                "Accessories",
                List.of(
                        new VariantSpec("BNI-NVY-OS", "5060123456091", "Color", "Navy",  new BigDecimal("9.50"), new BigDecimal("34.00"), 130, 10, 50),
                        new VariantSpec("BNI-CRM-OS", "5060123456092", "Color", "Cream", new BigDecimal("9.50"), new BigDecimal("34.00"), 130, 10, 45),
                        new VariantSpec("BNI-OLV-OS", "5060123456093", "Color", "Olive", new BigDecimal("9.50"), new BigDecimal("34.00"), 130, 10, 40)
                )));
        all.addAll(createAndSaveProduct("Recycled Puffer Vest",
                "100% post-consumer recycled polyester shell, 80g fill",
                "Apparel",
                List.of(
                        new VariantSpec("PVT-BLK-S",  "5060123456101", "Size / Color", "S / Black",  new BigDecimal("26.00"), new BigDecimal("79.00"), 380, 5, 22),
                        new VariantSpec("PVT-BLK-M",  "5060123456102", "Size / Color", "M / Black",  new BigDecimal("26.00"), new BigDecimal("79.00"), 400, 5, 30),
                        new VariantSpec("PVT-BLK-L",  "5060123456103", "Size / Color", "L / Black",  new BigDecimal("26.00"), new BigDecimal("79.00"), 420, 5, 25)
                )));
        all.addAll(createAndSaveProduct("Woven Bucket Hat",
                "Deadstock cotton canvas, wide brim, chin cord",
                "Accessories",
                List.of(
                        new VariantSpec("BKT-STR-OS", "5060123456111", "Color", "Straw",  new BigDecimal("7.00"), new BigDecimal("25.00"), 160, 10, 60),
                        new VariantSpec("BKT-NVY-OS", "5060123456112", "Color", "Navy",   new BigDecimal("7.00"), new BigDecimal("25.00"), 160, 10, 55)
                )));
        return all;
    }

    // 12 Coffee products, 3 variants each → ~36 variants
    private List<ProductVariant> seedCoffeeProducts() {
        List<ProductVariant> all = new ArrayList<>();
        all.addAll(createAndSaveProduct("Ethiopia Yirgacheffe Washed",
                "Heirloom varietal, floral jasmine and bergamot notes, Grade 1",
                "Coffee / Ethiopia",
                List.of(
                        new VariantSpec("COF-ETH-200G",  "5060789012001", "Weight / Grind", "200g / Whole Bean",    new BigDecimal("6.80"), new BigDecimal("15.50"), 210, 10, 80),
                        new VariantSpec("COF-ETH-250G",  "5060789012002", "Weight / Grind", "250g / Filter Grind",  new BigDecimal("8.20"), new BigDecimal("18.00"), 260, 10, 75),
                        new VariantSpec("COF-ETH-500G",  "5060789012003", "Weight / Grind", "500g / Whole Bean",    new BigDecimal("14.50"), new BigDecimal("32.00"), 510, 5, 50)
                )));
        all.addAll(createAndSaveProduct("Guatemala Huehuetenango Natural",
                "SHB-rated, dried on raised beds, stone fruit and dark chocolate finish",
                "Coffee / Guatemala",
                List.of(
                        new VariantSpec("COF-GTM-200G", "5060789012011", "Weight / Grind", "200g / Whole Bean",    new BigDecimal("6.20"), new BigDecimal("14.00"), 210, 10, 65),
                        new VariantSpec("COF-GTM-250G", "5060789012012", "Weight / Grind", "250g / Filter Grind",  new BigDecimal("7.50"), new BigDecimal("16.50"), 260, 10, 60),
                        new VariantSpec("COF-GTM-1KG",  "5060789012013", "Weight / Grind", "1kg / Whole Bean",     new BigDecimal("27.00"), new BigDecimal("58.00"), 1010, 5, 30)
                )));
        all.addAll(createAndSaveProduct("Colombia El Paraíso Double Fermentation",
                "Caturra varietal, 200h fermentation, tropical fruit complexity",
                "Coffee / Colombia",
                List.of(
                        new VariantSpec("COF-COL-200G", "5060789012021", "Weight / Grind", "200g / Whole Bean",   new BigDecimal("8.00"), new BigDecimal("19.00"), 210, 10, 50),
                        new VariantSpec("COF-COL-250G", "5060789012022", "Weight / Grind", "250g / Espresso",     new BigDecimal("9.50"), new BigDecimal("22.00"), 260, 10, 55),
                        new VariantSpec("COF-COL-500G", "5060789012023", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("17.00"), new BigDecimal("38.00"), 510, 5, 35)
                )));
        all.addAll(createAndSaveProduct("Kenya Nyeri AB Washed",
                "SL28 and SL34 varietals, tomato and blackcurrant brightness",
                "Coffee / Kenya",
                List.of(
                        new VariantSpec("COF-KNY-200G", "5060789012031", "Weight / Grind", "200g / Whole Bean",   new BigDecimal("7.50"), new BigDecimal("17.00"), 210, 10, 45),
                        new VariantSpec("COF-KNY-250G", "5060789012032", "Weight / Grind", "250g / Filter Grind", new BigDecimal("9.00"), new BigDecimal("20.00"), 260, 10, 50),
                        new VariantSpec("COF-KNY-500G", "5060789012033", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("16.00"), new BigDecimal("35.00"), 510, 5, 28)
                )));
        all.addAll(createAndSaveProduct("Panama Geisha Natural",
                "Hacienda La Esmeralda, peach blossom and jasmine, best of Panama rated",
                "Coffee / Panama",
                List.of(
                        new VariantSpec("COF-PAN-100G", "5060789012041", "Weight / Grind", "100g / Whole Bean",   new BigDecimal("18.00"), new BigDecimal("42.00"), 110, 5, 3),
                        new VariantSpec("COF-PAN-200G", "5060789012042", "Weight / Grind", "200g / Whole Bean",   new BigDecimal("34.00"), new BigDecimal("78.00"), 210, 5, 1)
                )));
        all.addAll(createAndSaveProduct("Rwanda Rulindo Honey",
                "Red bourbon, honey-processed, red apple and hazelnut sweetness",
                "Coffee / Rwanda",
                List.of(
                        new VariantSpec("COF-RWA-200G", "5060789012051", "Weight / Grind", "200g / Whole Bean",   new BigDecimal("6.50"), new BigDecimal("14.50"), 210, 10, 55),
                        new VariantSpec("COF-RWA-250G", "5060789012052", "Weight / Grind", "250g / Filter Grind", new BigDecimal("7.80"), new BigDecimal("17.00"), 260, 10, 48),
                        new VariantSpec("COF-RWA-500G", "5060789012053", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("14.00"), new BigDecimal("30.00"), 510, 5, 32)
                )));
        all.addAll(createAndSaveProduct("Brazil Matas de Minas Natural",
                "Yellow Bourbon, dry-processed, chocolate and caramel sweetness, perfect for espresso",
                "Coffee / Brazil",
                List.of(
                        new VariantSpec("COF-BRZ-250G", "5060789012061", "Weight / Grind", "250g / Espresso",     new BigDecimal("5.80"), new BigDecimal("13.00"), 260, 10, 80),
                        new VariantSpec("COF-BRZ-500G", "5060789012062", "Weight / Grind", "500g / Espresso",     new BigDecimal("10.50"), new BigDecimal("23.00"), 510, 5, 60),
                        new VariantSpec("COF-BRZ-1KG",  "5060789012063", "Weight / Grind", "1kg / Espresso",      new BigDecimal("19.00"), new BigDecimal("42.00"), 1010, 5, 40)
                )));
        all.addAll(createAndSaveProduct("Costa Rica Tarrazú Washed",
                "Caturra and Catuaí, full traceability micro-lot, bright and balanced",
                "Coffee / Costa Rica",
                List.of(
                        new VariantSpec("COF-CRI-200G", "5060789012071", "Weight / Grind", "200g / Whole Bean",   new BigDecimal("7.00"), new BigDecimal("16.00"), 210, 10, 45),
                        new VariantSpec("COF-CRI-250G", "5060789012072", "Weight / Grind", "250g / Filter Grind", new BigDecimal("8.30"), new BigDecimal("18.50"), 260, 10, 50),
                        new VariantSpec("COF-CRI-500G", "5060789012073", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("15.00"), new BigDecimal("33.00"), 510, 5, 30)
                )));
        all.addAll(createAndSaveProduct("Japan Shizuoka Green Tea",
                "Ceremonial grade, first flush, umami and fresh grass notes",
                "Tea / Japan",
                List.of(
                        new VariantSpec("TEA-SHZ-50G",  "5060789012081", "Weight", "50g",   new BigDecimal("12.00"), new BigDecimal("28.00"), 60, 10, 40),
                        new VariantSpec("TEA-SHZ-100G", "5060789012082", "Weight", "100g",  new BigDecimal("22.00"), new BigDecimal("52.00"), 110, 5, 30)
                )));
        all.addAll(createAndSaveProduct("Assam TGFOP Breakfast Blend",
                "Golden tip Flowery Orange Pekoe, malty and full-bodied",
                "Tea / India",
                List.of(
                        new VariantSpec("TEA-ASM-100G", "5060789012091", "Weight", "100g",  new BigDecimal("5.00"), new BigDecimal("12.00"), 110, 10, 60),
                        new VariantSpec("TEA-ASM-250G", "5060789012092", "Weight", "250g",  new BigDecimal("11.00"), new BigDecimal("25.00"), 260, 5, 45),
                        new VariantSpec("TEA-ASM-500G", "5060789012093", "Weight", "500g",  new BigDecimal("20.00"), new BigDecimal("45.00"), 510, 5, 25)
                )));
        all.addAll(createAndSaveProduct("Nordic Winter Blend",
                "House roast: Guatemala and Colombia, cinnamon, dark fruits, velvet mouthfeel",
                "Coffee / Blend",
                List.of(
                        new VariantSpec("COF-NRD-250G", "5060789012101", "Weight / Grind", "250g / Whole Bean",   new BigDecimal("7.50"), new BigDecimal("16.00"), 260, 10, 70),
                        new VariantSpec("COF-NRD-500G", "5060789012102", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("13.50"), new BigDecimal("29.00"), 510, 5, 50),
                        new VariantSpec("COF-NRD-1KG",  "5060789012103", "Weight / Grind", "1kg / Whole Bean",    new BigDecimal("25.00"), new BigDecimal("54.00"), 1010, 5, 30)
                )));
        all.addAll(createAndSaveProduct("Decaf Swiss Water Colombia",
                "99.9% caffeine-free, Swiss Water processed, chocolate and walnut",
                "Coffee / Decaf",
                List.of(
                        new VariantSpec("COF-DEC-250G", "5060789012111", "Weight / Grind", "250g / Filter Grind", new BigDecimal("8.50"), new BigDecimal("19.00"), 260, 10, 45),
                        new VariantSpec("COF-DEC-500G", "5060789012112", "Weight / Grind", "500g / Whole Bean",   new BigDecimal("15.50"), new BigDecimal("34.00"), 510, 5, 28)
                )));
        return all;
    }

    // 6 Coffee Accessory products → ~12 variants
    private List<ProductVariant> seedCoffeeAccessories() {
        List<ProductVariant> all = new ArrayList<>();
        all.addAll(createAndSaveProduct("Fellow Ode Brew Grinder Gen 2",
                "64mm flat steel burrs, 31 grind settings, single-dose design",
                "Coffee Gear / Grinders",
                List.of(
                        new VariantSpec("GRD-ODE-BLK", "5060345678001", "Color", "Matte Black",  new BigDecimal("165.00"), new BigDecimal("295.00"), 2700, 2, 12),
                        new VariantSpec("GRD-ODE-WHT", "5060345678002", "Color", "Matte White",  new BigDecimal("165.00"), new BigDecimal("295.00"), 2700, 2, 8)
                )));
        all.addAll(createAndSaveProduct("Hario V60 Ceramic Dripper 02",
                "Ceramic pour-over dripper, 60° angle ridges, compatible with tabbed filters",
                "Coffee Gear / Brewers",
                List.of(
                        new VariantSpec("V60-CRM-WHT", "5060345678011", "Color / Size", "White / 02",  new BigDecimal("12.00"), new BigDecimal("28.00"), 350, 5, 30),
                        new VariantSpec("V60-CRM-BLK", "5060345678012", "Color / Size", "Black / 02",  new BigDecimal("12.00"), new BigDecimal("28.00"), 350, 5, 25)
                )));
        all.addAll(createAndSaveProduct("Chemex Classic 6-Cup",
                "Non-porous borosilicate glass, wood collar, genuine leather tie",
                "Coffee Gear / Brewers",
                List.of(
                        new VariantSpec("CHM-6CUP-OS", "5060345678021", "Size", "6-Cup",  new BigDecimal("28.00"), new BigDecimal("55.00"), 750, 3, 20)
                )));
        all.addAll(createAndSaveProduct("AeroPress Original",
                "Total immersion brewing, compact travel-friendly, includes 350 micro-filters",
                "Coffee Gear / Brewers",
                List.of(
                        new VariantSpec("ARP-ORG-OS", "5060345678031", "Variant", "Original", new BigDecimal("22.00"), new BigDecimal("42.00"), 420, 5, 25),
                        new VariantSpec("ARP-GO-OS",  "5060345678032", "Variant", "Go Travel",  new BigDecimal("24.00"), new BigDecimal("46.00"), 380, 5, 20)
                )));
        all.addAll(createAndSaveProduct("Acaia Pearl Scale",
                "0.1g resolution, Bluetooth, auto-pour timer, USB-C charging",
                "Coffee Gear / Scales",
                List.of(
                        new VariantSpec("SCL-PRL-BLK", "5060345678041", "Color", "Black",  new BigDecimal("85.00"), new BigDecimal("155.00"), 880, 2, 10),
                        new VariantSpec("SCL-PRL-WHT", "5060345678042", "Color", "White",  new BigDecimal("85.00"), new BigDecimal("155.00"), 880, 2, 8)
                )));
        all.addAll(createAndSaveProduct("Hario Buono Drip Kettle",
                "800ml gooseneck kettle, precision pour spout, stainless steel, compatible with all hobs",
                "Coffee Gear / Kettles",
                List.of(
                        new VariantSpec("KTL-BNO-800", "5060345678051", "Capacity", "800ml", new BigDecimal("38.00"), new BigDecimal("72.00"), 980, 3, 15)
                )));
        return all;
    }

    // ──────────────────────────────────────────────────────────────────
    // Historical Orders
    // ──────────────────────────────────────────────────────────────────
    private void seedOrders() {
        if (orderRepository.count() > 0) return;

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime catalogAt = now.minusMonths(4).minusDays(10);
        UUID ownerId = userRepository.findByEmailIgnoreCase("owner@stockledger.io")
                .orElseThrow(() -> new IllegalStateException("Seed owner user not found"))
                .getId();

        List<SeedDataBackdater.OrderTimeline> timelines = new ArrayList<>();

        // Four months ago: first dispatch.
        OffsetDateTime firstOrderAt = now.minusMonths(4).plusDays(2);
        OrderResponse first = createManualOrder(
                "Freja Lindqvist", "freja.lindqvist@nordicliving.se",
                "Kungsgatan 14, 111 43 Stockholm, Sweden",
                new BigDecimal("8.00"), "HOOD-NVY-M", 2
        );
        packAndShip(first, Carrier.DHL, ParcelSize.MEDIUM, "1234567890", null);
        recordTimeline(timelines, first, firstOrderAt, firstOrderAt.plusDays(1), null, List.of());

        // Three months ago: CSV and platform-sync channels, including a full return.
        OffsetDateTime csvOrderAt = now.minusMonths(3).plusDays(6);
        OrderResponse csv = createReservedOrder(
                "ORD-2026-0102", OrderSource.CSV, "CSV-2026-0102",
                "Lars Berg", "lars.berg@fjallraven.se",
                "Drottninggatan 22, 411 07 Gothenburg, Sweden",
                new BigDecimal("6.50"), "COF-ETH-250G", 1, ownerId
        );
        packAndShip(csv, Carrier.ROYAL_MAIL, ParcelSize.SMALL, "RM987654321GB", null);
        recordTimeline(timelines, csv, csvOrderAt, csvOrderAt.plusDays(1), null, List.of());

        OffsetDateTime returnedOrderAt = now.minusMonths(3).plusDays(18);
        OffsetDateTime returnedAt = returnedOrderAt.plusDays(7);
        OrderResponse returned = createReservedOrder(
                "ORD-2026-0103", OrderSource.PLATFORM_SYNC, "SHP-2026-1042",
                "Erik Johansson", "erik.johansson@outdoorkit.se",
                "Birger Jarlsgatan 19, 114 34 Stockholm, Sweden",
                new BigDecimal("7.20"), "COF-ETH-200G", 2, ownerId
        );
        packAndShip(returned, Carrier.DHL, ParcelSize.SMALL, "2345678901", new BigDecimal("5.50"));
        ReturnResponse fullReturn = orderReturnService.processReturn(
                returned.id(),
                new ProcessReturnRequest(
                        "Customer reported a damaged outer bag",
                        "One bag was crushed in transit; one bag was still sellable and returned to stock.",
                        List.of(
                                new ProcessReturnRequest.ReturnItemRequest(
                                        returned.items().getFirst().id(), 1, ItemCondition.SELLABLE),
                                new ProcessReturnRequest.ReturnItemRequest(
                                        returned.items().getFirst().id(), 1, ItemCondition.DAMAGED)
                        )
                ),
                ownerId
        );
        recordTimeline(
                timelines, returned, returnedOrderAt, returnedOrderAt.plusDays(1),
                returnedAt, List.of(fullReturn.id())
        );

        // Two months ago: a dispatch and a partial return.
        OffsetDateTime secondShipAt = now.minusMonths(2).plusDays(4);
        OrderResponse secondShip = createManualOrder(
                "Astrid Holm", "astrid.holm@nordicwear.se",
                "Storgatan 5, 211 41 Malmo, Sweden",
                new BigDecimal("12.50"), "TEE-WHT-M", 3
        );
        packAndShip(secondShip, Carrier.FEDEX, ParcelSize.MEDIUM, "774411225533", null);
        recordTimeline(
                timelines, secondShip, secondShipAt, secondShipAt.plusDays(1), null, List.of()
        );

        OffsetDateTime partialOrderAt = now.minusMonths(2).plusDays(16);
        OffsetDateTime partialReturnAt = partialOrderAt.plusDays(5);
        OrderResponse partial = createManualOrder(
                "Oscar Nilsson", "oscar.nilsson@kaffebolaget.se",
                "Linnégatan 8, 413 04 Gothenburg, Sweden",
                new BigDecimal("7.90"), "QZ-OAT-M", 3
        );
        packAndShip(partial, Carrier.UPS, ParcelSize.MEDIUM, "1Z999AA10123456784", null);
        ReturnResponse partialReturn = orderReturnService.processReturn(
                partial.id(),
                new ProcessReturnRequest(
                        "Sleeve length did not suit the customer",
                        "Customer kept two sweatshirts and returned the third unopened.",
                        List.of(new ProcessReturnRequest.ReturnItemRequest(
                                partial.items().getFirst().id(), 1, ItemCondition.SELLABLE))
                ),
                ownerId
        );
        recordTimeline(
                timelines, partial, partialOrderAt, partialOrderAt.plusDays(1),
                partialReturnAt, List.of(partialReturn.id())
        );

        // One month ago: another dispatch, a cancellation, and the out-of-stock sale.
        OffsetDateTime thirdShipAt = now.minusMonths(1).plusDays(3);
        OrderResponse thirdShip = createManualOrder(
                "Birgitta Larsson", "birgitta.larsson@hemtextil.se",
                "Vasagatan 11, 111 20 Stockholm, Sweden",
                new BigDecimal("6.25"), "COF-GTM-250G", 2
        );
        packAndShip(thirdShip, Carrier.ROYAL_MAIL, ParcelSize.MEDIUM, "RM123456789GB", null);
        recordTimeline(
                timelines, thirdShip, thirdShipAt, thirdShipAt.plusDays(1), null, List.of()
        );

        OffsetDateTime cancelledAt = now.minusMonths(1).plusDays(20);
        OrderResponse cancelled = createManualOrder(
                "Sofia Andersson", "sofia.andersson@nordicnest.se",
                "Luntmakargatan 28, 113 58 Stockholm, Sweden",
                new BigDecimal("5.80"), "CAP-BLK-OS", 2
        );
        orderService.cancelOrder(cancelled.id(), "Customer changed delivery address", ownerId);
        recordTimeline(timelines, cancelled, cancelledAt, null, null, List.of());

        OffsetDateTime outOfStockSaleAt = now.minusMonths(1).plusDays(8);
        OrderResponse outOfStockSale = createManualOrder(
                "Nils Svensson", "nils.svensson@fjallraven.se",
                "Davidshallsgatan 4, 211 45 Malmo, Sweden",
                new BigDecimal("7.20"), "COF-PAN-200G", 1
        );
        packAndShip(outOfStockSale, Carrier.DHL, ParcelSize.SMALL, "3456789012", null);
        recordTimeline(
                timelines, outOfStockSale, outOfStockSaleAt, outOfStockSaleAt.plusDays(1),
                null, List.of()
        );

        // Recent activity: dispatched, ready to ship, pending, and waiting for stock.
        OffsetDateTime recentShipAt = now.minusDays(12);
        OrderResponse recentShip = createManualOrder(
                "Maja Magnusson", "maja.magnusson@stockholmsroastery.se",
                "Kyrkogatan 31, 411 08 Gothenburg, Sweden",
                new BigDecimal("5.80"), "COF-KNY-250G", 1
        );
        packAndShip(recentShip, Carrier.UPS, ParcelSize.SMALL, "1Z999AA10123456785", null);
        recordTimeline(
                timelines, recentShip, recentShipAt, recentShipAt.plusDays(1), null, List.of()
        );

        OffsetDateTime readyAt = now.minusDays(9);
        OrderResponse ready = createManualOrder(
                "Henrik Persson", "henrik.persson@outnorth.se",
                "Folkungagatan 88, 116 30 Stockholm, Sweden",
                new BigDecimal("6.00"), "TOT-BLK-OS", 2
        );
        orderService.packOrder(ready.id());
        recordTimeline(timelines, ready, readyAt, null, null, List.of());

        OffsetDateTime pendingAt = now.minusDays(4);
        OrderResponse pending = createManualOrder(
                "Elin Sundberg", "elin.sundberg@nordicliving.se",
                "Vasagatan 7, 111 20 Stockholm, Sweden",
                new BigDecimal("5.50"), "MRN-CRM-M", 1
        );
        recordTimeline(timelines, pending, pendingAt, null, null, List.of());

        OffsetDateTime waitingAt = now.minusDays(2);
        ProductVariant outOfStockVariant = variant("COF-PAN-200G");
        Order waiting = Order.createWaiting(
                "ORD-2026-0110",
                OrderSource.PLATFORM_SYNC,
                "SHP-2026-0110",
                "Oskar Berg",
                "oskar.berg@kaffebolaget.se",
                "Kungsgatan 18, 411 19 Gothenburg, Sweden",
                new BigDecimal("6.25")
        );
        waiting.addItem(OrderItem.createUnreserved(
                waiting,
                outOfStockVariant,
                2,
                outOfStockVariant.getSellingPrice()
        ));
        Order savedWaiting = orderRepository.saveAndFlush(waiting);
        timelines.add(new SeedDataBackdater.OrderTimeline(
                savedWaiting.getId(),
                waitingAt,
                waitingAt.plusHours(1),
                null,
                null,
                List.of()
        ));

        entityManager.flush();
        seedDataBackdater.backdateInitialReceipts(catalogAt);
        timelines.forEach(seedDataBackdater::backdateOrder);
    }

    private OrderResponse createManualOrder(
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal customerShippingCharge,
            String sku,
            int quantity
    ) {
        ProductVariant variant = variant(sku);
        return orderService.createManualOrder(new CreateOrderRequest(
                customerName,
                customerEmail,
                shippingAddress,
                customerShippingCharge,
                List.of(new CreateOrderItemRequest(sku, quantity, variant.getSellingPrice()))
        ));
    }

    private OrderResponse createReservedOrder(
            String orderNumber,
            OrderSource source,
            String externalOrderId,
            String customerName,
            String customerEmail,
            String shippingAddress,
            BigDecimal customerShippingCharge,
            String sku,
            int quantity,
            UUID actorId
    ) {
        ProductVariant variant = variant(sku);
        Order order = Order.create(
                orderNumber,
                source,
                externalOrderId,
                customerName,
                customerEmail,
                shippingAddress,
                customerShippingCharge
        );
        order.addItem(OrderItem.create(order, variant, quantity, variant.getSellingPrice()));
        inventoryService.reserveStock(
                variant.getId(),
                quantity,
                order.getId(),
                actorId,
                "Order placed: " + orderNumber
        );
        orderRepository.saveAndFlush(order);
        Order saved = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "Seeded order was not persisted: " + orderNumber
                ));
        return orderService.mapToResponse(saved);
    }

    private void packAndShip(
            OrderResponse order,
            Carrier carrier,
            ParcelSize parcelSize,
            String trackingNumber,
            BigDecimal actualShippingCostOverride
    ) {
        orderService.packOrder(order.id());
        orderService.shipOrder(
                order.id(),
                new ShipOrderRequest(
                        carrier,
                        parcelSize,
                        trackingNumber,
                        actualShippingCostOverride
                )
        );
    }

    private void recordTimeline(
            List<SeedDataBackdater.OrderTimeline> timelines,
            OrderResponse order,
            OffsetDateTime createdAt,
            OffsetDateTime shippedAt,
            OffsetDateTime returnedAt,
            List<UUID> returnIds
    ) {
        OrderStatus status = orderRepository.findById(order.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Seeded order disappeared: " + order.id()
                ))
                .getStatus();

        OffsetDateTime updatedAt;
        if (returnedAt != null) {
            updatedAt = returnedAt;
        } else if (shippedAt != null) {
            updatedAt = shippedAt;
        } else if (status == OrderStatus.CANCELLED) {
            updatedAt = createdAt.plusDays(1);
        } else if (status == OrderStatus.READY_TO_SHIP) {
            updatedAt = createdAt.plusHours(6);
        } else {
            updatedAt = createdAt.plusHours(1);
        }

        timelines.add(new SeedDataBackdater.OrderTimeline(
                order.id(),
                createdAt,
                updatedAt,
                shippedAt,
                returnedAt,
                returnIds
        ));
    }

    private ProductVariant variant(String sku) {
        return productVariantRepository.findBySkuIgnoreCase(sku)
                .orElseThrow(() -> new IllegalStateException("Seed variant not found: " + sku));
    }
}
