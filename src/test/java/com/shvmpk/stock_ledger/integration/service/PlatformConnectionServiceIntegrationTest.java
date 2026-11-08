package com.shvmpk.stock_ledger.integration.service;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.common.error.ConcurrentChangeException;
import com.shvmpk.stock_ledger.common.error.InvalidIdempotencyKeyException;
import com.shvmpk.stock_ledger.common.error.InvalidPlatformCredentialsException;
import com.shvmpk.stock_ledger.common.error.InvalidPlatformEndpointException;
import com.shvmpk.stock_ledger.common.error.InvalidPlatformVersionException;
import com.shvmpk.stock_ledger.common.error.NoPlatformChangesException;
import com.shvmpk.stock_ledger.common.error.PauseReasonRequiredException;
import com.shvmpk.stock_ledger.common.error.PlatformWebhookAddressInUseException;
import com.shvmpk.stock_ledger.common.error.ResourceNotFoundException;
import com.shvmpk.stock_ledger.common.error.UnsupportedPlatformTypeException;
import com.shvmpk.stock_ledger.platform.dto.CreatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.dto.PlatformConnectionResponse;
import com.shvmpk.stock_ledger.platform.dto.UpdatePlatformConnectionRequest;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionChangeType;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnectionEvent;
import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionEventRepository;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialsCodec;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionCreationResult;
import com.shvmpk.stock_ledger.platform.service.PlatformConnectionService;
import com.shvmpk.stock_ledger.support.PlatformConnectionEventTestSupport;
import com.shvmpk.stock_ledger.user.entity.Role;
import com.shvmpk.stock_ledger.user.entity.User;
import com.shvmpk.stock_ledger.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Platform Connection Service Integration Tests")
class PlatformConnectionServiceIntegrationTest {

    private static final String SHOPIFY_ENDPOINT = "https://northwind-supply.myshopify.com";
    private static final String SHOPIFY_ARCHIVE_ENDPOINT = "https://northwind-supply-archive.myshopify.com";
    private static final String HARBOUR_SHOPIFY_ENDPOINT = "https://harbour-and-pine.myshopify.com";
    private static final String WOOCOMMERCE_ENDPOINT = "https://northwind-home.co.uk";
    private static final String SHOPIFY_WEBHOOK_SECRET = "dummy-webhook-secret";
    private static final String WOOCOMMERCE_WEBHOOK_SECRET = "dummy-webhook-secret-3";
    private static final String SHOPIFY_ACCESS_TOKEN = "dummy-shopify-access-token";
    private static final String WOOCOMMERCE_CONSUMER_KEY = "test-woocommerce-consumer-key";
    private static final String WOOCOMMERCE_CONSUMER_SECRET = "dummy-woocommerce-secret";
    private static final String PAUSE_REASON = "Store closed for a seasonal break.";

    @Autowired
    private PlatformConnectionService service;

    @Autowired
    private PlatformConnectionRepository connectionRepository;

    @Autowired
    private PlatformConnectionEventRepository eventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformCredentialCipher credentialCipher;

    @Autowired
    private PlatformCredentialsCodec credentialsCodec;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;

    @BeforeEach
    void setUp() {
        PlatformConnectionEventTestSupport.clear(jdbcTemplate);
        connectionRepository.deleteAllInBatch();
        owner = userRepository.findByEmailIgnoreCase("elin.sandberg@northwind-supply.co")
                .orElseGet(() -> userRepository.save(User.create(
                        UUID.randomUUID(),
                        "elin.sandberg@northwind-supply.co",
                        passwordEncoder.encode("Northwind-Supply-Owner-2026!"),
                        "Elin Sandberg",
                        Role.OWNER
                )));
    }

    @Test
    @DisplayName("Should create an encrypted Shopify connection and return only safe fields")
    void shouldCreateShopifyConnection() {
        PlatformConnectionResponse response = createShopify(SHOPIFY_ENDPOINT + "/", SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN);

        assertThat(response.platformType()).isEqualTo(PlatformType.SHOPIFY);
        assertThat(response.apiEndpoint()).isEqualTo(SHOPIFY_ENDPOINT);
        assertThat(response.webhookPath()).isEqualTo("/api/v1/webhooks/orders/shopify");
        assertThat(response.syncStatus()).isEqualTo("PENDING");
        assertThat(response.syncSupported()).isTrue();
        assertThat(response.active()).isTrue();
        assertThat(response.webhookConfigured()).isTrue();
        assertThat(response.credentialsConfigured()).isTrue();
        assertThat(response.lastSyncAt()).isNull();
        assertThat(response.lastErrorMessage()).isNull();
        assertThat(response.deactivatedAt()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        PlatformConnection connection = connectionRepository.findById(response.id()).orElseThrow();
        assertThat(connection.getWebhookSecretEncrypted())
                .startsWith("v1.")
                .doesNotContain(SHOPIFY_WEBHOOK_SECRET);
        assertThat(credentialCipher.decrypt(connection.getWebhookSecretEncrypted(), "webhookSecret"))
                .isEqualTo(SHOPIFY_WEBHOOK_SECRET);

        assertThat(connection.getEncryptedApiCredentials())
                .startsWith("v1.")
                .doesNotContain(SHOPIFY_ACCESS_TOKEN);
        PlatformCredentials credentials = credentialsCodec.decode(
                credentialCipher.decrypt(connection.getEncryptedApiCredentials(), "apiCredentials")
        );
        assertThat(credentials.accessToken()).isEqualTo(SHOPIFY_ACCESS_TOKEN);
        assertThat(credentials.consumerKey()).isNull();
        assertThat(credentials.consumerSecret()).isNull();
    }

    @Test
    @DisplayName("Should create a WooCommerce connection with consumer key and secret credentials")
    void shouldCreateWooCommerceConnection() {
        PlatformConnectionResponse response = createWooCommerce();

        assertThat(response.platformType()).isEqualTo(PlatformType.WOOCOMMERCE);
        assertThat(response.apiEndpoint()).isEqualTo(WOOCOMMERCE_ENDPOINT);
        assertThat(response.webhookPath()).isEqualTo("/api/v1/webhooks/orders/woocommerce");
        assertThat(response.syncSupported()).isTrue();
        assertThat(response.webhookConfigured()).isTrue();
        assertThat(response.credentialsConfigured()).isTrue();

        PlatformConnection connection = connectionRepository.findById(response.id()).orElseThrow();
        PlatformCredentials credentials = credentialsCodec.decode(
                credentialCipher.decrypt(connection.getEncryptedApiCredentials(), "apiCredentials")
        );
        assertThat(credentials.accessToken()).isNull();
        assertThat(credentials.consumerKey()).isEqualTo(WOOCOMMERCE_CONSUMER_KEY);
        assertThat(credentials.consumerSecret()).isEqualTo(WOOCOMMERCE_CONSUMER_SECRET);
    }

    @Test
    @DisplayName("Should take the default display name from the platform catalogue")
    void shouldUseCatalogueDisplayNameByDefault() {
        PlatformConnectionResponse response = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThat(response.displayName()).isEqualTo("Shopify");
        assertThat(response.slug()).isEqualTo("shopify");
    }

    @Test
    @DisplayName("Should create an inbound-only channel without API credentials")
    void shouldCreateInboundOnlyConnectionWithoutCredentials() {
        PlatformConnectionResponse response = service.create(new CreatePlatformConnectionRequest(
                PlatformType.SQUARE,
                "https://northwind-supply.square.site",
                "dummy-webhook-secret-4",
                null,
                null,
                null
        ), owner.getId());

        assertThat(response.platformType()).isEqualTo(PlatformType.SQUARE);
        assertThat(response.displayName()).isEqualTo("Square POS");
        assertThat(response.syncSupported()).isFalse();
        assertThat(response.credentialsConfigured()).isFalse();
        assertThat(response.webhookConfigured()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("Should reject Etsy until an adapter exists")
    void shouldRejectEtsyConnection() {
        assertThatThrownBy(() -> service.create(new CreatePlatformConnectionRequest(
                PlatformType.ETSY,
                "https://northwind-vintage.etsy.com",
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN,
                null,
                null
        ), owner.getId()))
                .isInstanceOf(UnsupportedPlatformTypeException.class)
                .extracting("errorCode")
                .isEqualTo("UNSUPPORTED_PLATFORM_TYPE");

        assertThat(connectionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should reject missing platform-specific credentials")
    void shouldRejectMissingPlatformCredentials() {
        assertThatThrownBy(() -> createShopify(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, null))
                .isInstanceOf(InvalidPlatformCredentialsException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_CREDENTIALS");

        assertThatThrownBy(() -> service.create(new CreatePlatformConnectionRequest(
                PlatformType.WOOCOMMERCE,
                WOOCOMMERCE_ENDPOINT,
                WOOCOMMERCE_WEBHOOK_SECRET,
                null,
                WOOCOMMERCE_CONSUMER_KEY,
                null
        ), owner.getId()))
                .isInstanceOf(InvalidPlatformCredentialsException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_CREDENTIALS");

        assertThat(connectionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should reject blank credential fields supplied on create")
    void shouldRejectBlankCredentialFields() {
        assertThatThrownBy(() -> createShopify(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, "   "))
                .isInstanceOf(InvalidPlatformCredentialsException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_CREDENTIALS");
    }

    @Test
    @DisplayName("Should reject an unsafe endpoint before persisting")
    void shouldRejectUnsafeEndpoint() {
        // A webhook URL with query parameters (here: partner-portal tracking
        // parameters) is unsafe in every profile, including the test profile's
        // allow-insecure-endpoints escape hatch, and must never be persisted.
        assertThatThrownBy(() -> createShopify(
                "https://northwind-supply.myshopify.com/?utm_source=partner-portal",
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        ))
                .isInstanceOf(InvalidPlatformEndpointException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_ENDPOINT");

        assertThat(connectionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should reject a second store that reuses the same webhook address")
    void shouldRejectAStoreReusingTheSameWebhookAddress() {
        createShopify(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN);

        assertThatThrownBy(() -> createShopify(
                SHOPIFY_ARCHIVE_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        ))
                .isInstanceOf(PlatformWebhookAddressInUseException.class)
                .hasMessageContaining("/api/v1/webhooks/orders/shopify")
                .extracting("errorCode")
                .isEqualTo("PLATFORM_WEBHOOK_ADDRESS_IN_USE");

        assertThat(connectionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should refuse to resume a store whose webhook address has been taken")
    void shouldRejectResumingWhenTheWebhookAddressIsTaken() {
        PlatformConnectionResponse original = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        service.deactivate(original.id(), PAUSE_REASON, owner.getId());
        createShopify(
                SHOPIFY_ARCHIVE_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        );

        assertThatThrownBy(() -> service.update(
                original.id(),
                new UpdatePlatformConnectionRequest(
                        service.get(original.id()).version(),
                        null, null, null, null, null, null, true
                ),
                owner.getId()
        ))
                .isInstanceOf(PlatformWebhookAddressInUseException.class)
                .hasMessageContaining("/api/v1/webhooks/orders/shopify")
                .extracting("errorCode")
                .isEqualTo("PLATFORM_WEBHOOK_ADDRESS_IN_USE");

        assertThat(service.get(original.id()).active()).isFalse();
    }

    @Test
    @DisplayName("Should pause and resume one of two stores of the same platform without touching the other")
    void shouldPauseAndResumeTwoStoresOfTheSamePlatformIndependently() {
        PlatformConnectionResponse northwind = createNamedShopify(
                "Northwind Supply",
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        PlatformConnectionResponse harbour = createNamedShopify(
                "Harbour & Pine",
                HARBOUR_SHOPIFY_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        );

        service.update(
                harbour.id(),
                new UpdatePlatformConnectionRequest(
                        harbour.version(),
                        null, null, null, null, null, null, false,
                        PAUSE_REASON
                ),
                owner.getId()
        );

        assertThat(service.get(harbour.id()).active()).isFalse();
        assertThat(service.get(northwind.id()).active()).isTrue();

        PlatformConnectionResponse resumed = service.update(
                harbour.id(),
                new UpdatePlatformConnectionRequest(
                        service.get(harbour.id()).version(),
                        null, null, null, null, null, null, true
                ),
                owner.getId()
        );

        assertThat(resumed.active()).isTrue();
        assertThat(resumed.deactivatedAt()).isNull();
        assertThat(service.get(northwind.id()).active()).isTrue();
        assertThat(resumed.slug()).isEqualTo("harbour-pine");
        assertThat(service.get(northwind.id()).slug()).isEqualTo("northwind-supply");
    }


    @Test
    @DisplayName("Should require a reason to pause and keep it on the card and in history")
    void shouldRequireAndRecordPauseReason() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThatThrownBy(() -> service.update(created.id(), new UpdatePlatformConnectionRequest(
                created.version(),
                null, null, null, null, null, null, false
        ), owner.getId()))
                .isInstanceOf(PauseReasonRequiredException.class)
                .extracting("errorCode")
                .isEqualTo("PAUSE_REASON_REQUIRED");

        PlatformConnectionResponse paused = service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, null, null, null, false,
                "  Store closed for a seasonal break.  "
        ), owner.getId());

        assertThat(paused.active()).isFalse();
        assertThat(paused.pauseReason()).isEqualTo(PAUSE_REASON);

        PlatformConnectionEvent pausedEvent = eventsFor(created.id()).stream()
                .filter(event -> event.getChangeType() == PlatformConnectionChangeType.PAUSED)
                .findFirst()
                .orElseThrow();
        assertThat(pausedEvent.getReason()).isEqualTo(PAUSE_REASON);

        PlatformConnectionResponse resumed = service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, null, null, null, true
        ), owner.getId());

        assertThat(resumed.active()).isTrue();
        assertThat(resumed.pauseReason()).isNull();
        assertThat(eventsFor(created.id()).stream()
                .filter(event -> event.getChangeType() == PlatformConnectionChangeType.RESUMED)
                .findFirst()
                .orElseThrow()
                .getReason()).isNull();
    }

    @Test
    @DisplayName("Should require a reason when pausing through the deactivate action")
    void shouldRequireReasonWhenDeactivating() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThatThrownBy(() -> service.deactivate(created.id(), "   ", owner.getId()))
                .isInstanceOf(PauseReasonRequiredException.class);

        PlatformConnectionResponse paused = service.deactivate(
                created.id(),
                PAUSE_REASON,
                owner.getId()
        );

        assertThat(paused.active()).isFalse();
        assertThat(paused.pauseReason()).isEqualTo(PAUSE_REASON);
        assertThat(service.get(created.id()).pauseReason()).isEqualTo(PAUSE_REASON);
    }

    @Test
    @DisplayName("Should allow active connections for different platform types")
    void shouldAllowDifferentPlatformTypes() {
        createShopify(SHOPIFY_ENDPOINT, SHOPIFY_WEBHOOK_SECRET, SHOPIFY_ACCESS_TOKEN);
        createWooCommerce();

        assertThat(connectionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should update endpoint and webhook secret without re-encrypting unchanged credentials")
    void shouldUpdateEndpointAndWebhookSecret() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        PlatformConnection before = connectionRepository.findById(created.id()).orElseThrow();
        String credentialsCiphertextBefore = before.getEncryptedApiCredentials();
        String webhookCiphertextBefore = before.getWebhookSecretEncrypted();

        PlatformConnectionResponse updated = service.update(created.id(), new UpdatePlatformConnectionRequest(
                created.version(),
                null,
                SHOPIFY_ENDPOINT + "/admin/",
                "whsec_new_7a2c9e4f1b6d8305",
                null,
                null,
                null,
                null
        ), owner.getId());

        assertThat(updated.apiEndpoint()).isEqualTo(SHOPIFY_ENDPOINT + "/admin");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());

        PlatformConnection after = connectionRepository.findById(created.id()).orElseThrow();
        assertThat(after.getEncryptedApiCredentials()).isEqualTo(credentialsCiphertextBefore);
        assertThat(after.getWebhookSecretEncrypted())
                .isNotEqualTo(webhookCiphertextBefore)
                .doesNotContain("whsec_new_7a2c9e4f1b6d8305");
        assertThat(credentialCipher.decrypt(after.getWebhookSecretEncrypted(), "webhookSecret"))
                .isEqualTo("whsec_new_7a2c9e4f1b6d8305");
    }

    @Test
    @DisplayName("Should merge partial credential updates and preserve the other fields")
    void shouldMergePartialCredentialUpdate() {
        PlatformConnectionResponse created = createWooCommerce();

        PlatformConnectionResponse updated = service.update(created.id(), new UpdatePlatformConnectionRequest(
                created.version(),
                null,
                null,
                null,
                null,
                null,
                "cs_6a1e9d3b7f2c4058a1e9d6b3",
                null
        ), owner.getId());

        assertThat(updated.credentialsConfigured()).isTrue();

        PlatformConnection connection = connectionRepository.findById(created.id()).orElseThrow();
        PlatformCredentials credentials = credentialsCodec.decode(
                credentialCipher.decrypt(connection.getEncryptedApiCredentials(), "apiCredentials")
        );
        assertThat(credentials.consumerKey()).isEqualTo(WOOCOMMERCE_CONSUMER_KEY);
        assertThat(credentials.consumerSecret()).isEqualTo("cs_6a1e9d3b7f2c4058a1e9d6b3");
    }

    @Test
    @DisplayName("Should reject an empty update and blank supplied secrets")
    void shouldRejectEmptyAndBlankUpdates() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        created.version(),
                        null, null, null, null, null, null, null
                ),
                owner.getId()
        ))
                .isInstanceOf(NoPlatformChangesException.class)
                .extracting("errorCode")
                .isEqualTo("NO_PLATFORM_CHANGES");

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        created.version(),
                        null, null, "   ", null, null, null, null
                ),
                owner.getId()
        ))
                .isInstanceOf(InvalidPlatformCredentialsException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_CREDENTIALS");

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        created.version(),
                        null, null, null, "", null, null, null
                ),
                owner.getId()
        ))
                .isInstanceOf(InvalidPlatformCredentialsException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_CREDENTIALS");
    }

    @Test
    @DisplayName("Should reject updating an unsupported Etsy row")
    void shouldRejectEtsyUpdate() {
        PlatformConnection etsy = connectionRepository.saveAndFlush(PlatformConnection.create(
                UUID.randomUUID(),
                PlatformType.ETSY,
                "https://northwind-vintage.etsy.com",
                credentialCipher.encrypt(SHOPIFY_WEBHOOK_SECRET, "webhookSecret"),
                credentialCipher.encrypt("{}", "apiCredentials")
        ));

        assertThatThrownBy(() -> service.update(
                etsy.getId(),
                new UpdatePlatformConnectionRequest(
                        etsy.getVersion(),
                        null, null, null, null, null, null, false,
                        PAUSE_REASON
                ),
                owner.getId()
        ))
                .isInstanceOf(UnsupportedPlatformTypeException.class)
                .extracting("errorCode")
                .isEqualTo("UNSUPPORTED_PLATFORM_TYPE");
    }

    @Test
    @DisplayName("Should deactivate idempotently, retain credentials, and reactivate after conflicts clear")
    void shouldDeactivateAndReactivate() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        PlatformConnection before = connectionRepository.findById(created.id()).orElseThrow();
        String credentialsCiphertext = before.getEncryptedApiCredentials();

        PlatformConnectionResponse deactivated = service.deactivate(created.id(), PAUSE_REASON, owner.getId());
        OffsetDateTime firstDeactivatedAt = deactivated.deactivatedAt();

        assertThat(deactivated.active()).isFalse();
        assertThat(firstDeactivatedAt).isNotNull();

        PlatformConnectionResponse deactivatedAgain = service.deactivate(created.id(), PAUSE_REASON, owner.getId());
        assertThat(deactivatedAgain.active()).isFalse();
        assertThat(deactivatedAgain.deactivatedAt()).isEqualTo(firstDeactivatedAt);

        PlatformConnection retained = connectionRepository.findById(created.id()).orElseThrow();
        assertThat(retained.getEncryptedApiCredentials()).isEqualTo(credentialsCiphertext);
        assertThat(credentialCipher.decrypt(retained.getWebhookSecretEncrypted(), "webhookSecret"))
                .isEqualTo(SHOPIFY_WEBHOOK_SECRET);

        PlatformConnectionResponse replacement = createShopify(
                SHOPIFY_ARCHIVE_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        );

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        service.get(created.id()).version(),
                        null, null, null, null, null, null, true
                ),
                owner.getId()
        ))
                .isInstanceOf(PlatformWebhookAddressInUseException.class)
                .extracting("errorCode")
                .isEqualTo("PLATFORM_WEBHOOK_ADDRESS_IN_USE");

        service.deactivate(replacement.id(), PAUSE_REASON, owner.getId());
        PlatformConnectionResponse reactivated = service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        service.get(created.id()).version(),
                        null, null, null, null, null, null, true
                ),
                owner.getId()
        );

        assertThat(reactivated.active()).isTrue();
        assertThat(reactivated.deactivatedAt()).isNull();
    }

    @Test
    @DisplayName("Should list active first, then by platform type, and support active filtering")
    void shouldListConnections() {
        PlatformConnectionResponse shopify = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        service.deactivate(shopify.id(), PAUSE_REASON, owner.getId());
        PlatformConnectionResponse replacement = createShopify(
                SHOPIFY_ARCHIVE_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        );
        PlatformConnectionResponse woo = createWooCommerce();

        List<PlatformConnectionResponse> all = service.list(null);
        assertThat(all)
                .extracting(PlatformConnectionResponse::id)
                .containsExactly(replacement.id(), woo.id(), shopify.id());

        assertThat(service.list(true))
                .extracting(PlatformConnectionResponse::id)
                .containsExactly(replacement.id(), woo.id());
        assertThat(service.list(false))
                .extracting(PlatformConnectionResponse::id)
                .containsExactly(shopify.id());
    }

    @Test
    @DisplayName("Should record creation with the actor, platform type, and no affected fields")
    void shouldRecordCreation() {
        PlatformConnectionResponse created = createNamedShopify(
                "Northwind Supply",
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThat(eventsFor(created.id())).singleElement().satisfies(event -> {
            assertThat(event.getChangeType()).isEqualTo(PlatformConnectionChangeType.CREATED);
            assertThat(event.getPlatformConnectionId()).isEqualTo(created.id());
            assertThat(event.getPlatformType()).isEqualTo("SHOPIFY");
            assertThat(event.getActorUserId()).isEqualTo(owner.getId());
            assertThat(event.getActorEmail()).isEqualTo(owner.getEmail());
            assertThat(event.getFields()).isEmpty();
            assertThat(event.getOccurredAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should record each kind of change exactly once, naming only the affected fields")
    void shouldRecordEachKindOfChangeExactlyOnce() {
        PlatformConnectionResponse created = createNamedShopify(
                "Northwind Supply",
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        rename(created.id(), "Northwind Supply EU", created.version());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null,
                SHOPIFY_ENDPOINT + "/admin",
                null, null, null, null, null
        ), owner.getId());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, "whsec_7b3d1f9a2c6e4058", null, null, null, null
        ), owner.getId());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, "shpat_9e2c5a7b1d4f8360c2a9e5b7", null, null, null
        ), owner.getId());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, null, null, null, false,
                PAUSE_REASON
        ), owner.getId());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, null, null, null, true
        ), owner.getId());

        List<PlatformConnectionEvent> events = eventsFor(created.id());

        assertThat(events).hasSize(7);
        assertThat(events)
                .filteredOn(event -> event.getChangeType() == PlatformConnectionChangeType.CREATED)
                .hasSize(1);
        assertThat(events)
                .filteredOn(event -> event.getChangeType() == PlatformConnectionChangeType.UPDATED)
                .extracting(PlatformConnectionEvent::getFields)
                .containsExactlyInAnyOrder(
                        List.of("displayName"),
                        List.of("apiEndpoint"),
                        List.of("webhookSecret"),
                        List.of("apiCredentials")
                );
        assertThat(events)
                .filteredOn(event -> event.getChangeType() == PlatformConnectionChangeType.PAUSED)
                .singleElement()
                .extracting(PlatformConnectionEvent::getFields)
                .isEqualTo(List.of("active"));
        assertThat(events)
                .filteredOn(event -> event.getChangeType() == PlatformConnectionChangeType.RESUMED)
                .singleElement()
                .extracting(PlatformConnectionEvent::getFields)
                .isEqualTo(List.of("active"));
    }

    @Test
    @DisplayName("Should not record a save that changes nothing")
    void shouldNotRecordAChangeThatChangesNothing() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        service.update(created.id(), new UpdatePlatformConnectionRequest(
                created.version(),
                null, SHOPIFY_ENDPOINT, null, null, null, null, null
        ), owner.getId());
        service.deactivate(created.id(), PAUSE_REASON, owner.getId());
        service.deactivate(created.id(), PAUSE_REASON, owner.getId());
        service.update(created.id(), new UpdatePlatformConnectionRequest(
                service.get(created.id()).version(),
                null, null, null, null, null, null, false,
                PAUSE_REASON
        ), owner.getId());

        assertThat(eventsFor(created.id()))
                .extracting(PlatformConnectionEvent::getChangeType)
                .containsExactlyInAnyOrder(
                        PlatformConnectionChangeType.CREATED,
                        PlatformConnectionChangeType.PAUSED
                );
    }

    @Test
    @DisplayName("Should never store a secret value in any history field")
    void shouldNeverStoreSecretValuesInHistory() {
        String rotatedSecret = "whsec_4a8e2c6f1b9d7035";
        String rotatedToken = "shpat_3d7f9b2c5e8a1046d3f7b9c2";
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        service.update(created.id(), new UpdatePlatformConnectionRequest(
                created.version(),
                null, null, rotatedSecret, rotatedToken, null, null, null
        ), owner.getId());
        service.deactivate(created.id(), PAUSE_REASON, owner.getId());

        List<String> rows = jdbcTemplate.queryForList("""
                SELECT to_jsonb(event)::text
                FROM platform_connection_events event
                WHERE platform_connection_id = ?
                """, String.class, created.id());

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(row)
                .doesNotContain(SHOPIFY_WEBHOOK_SECRET)
                .doesNotContain(SHOPIFY_ACCESS_TOKEN)
                .doesNotContain(rotatedSecret)
                .doesNotContain(rotatedToken));
        assertThat(eventsFor(created.id()))
                .flatExtracting(PlatformConnectionEvent::getFields)
                .containsOnly("webhookSecret", "apiCredentials", "active");
    }

    @Test
    @DisplayName("Should not record a change that was rejected")
    void shouldNotRecordRejectedChanges() {
        PlatformConnectionResponse original = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        service.deactivate(original.id(), PAUSE_REASON, owner.getId());
        createShopify(
                SHOPIFY_ARCHIVE_ENDPOINT,
                "dummy-webhook-secret-5",
                "shpat_1a9c4e7b2d6f8350a4c7e1b9"
        );

        assertThatThrownBy(() -> service.update(original.id(), new UpdatePlatformConnectionRequest(
                service.get(original.id()).version(),
                null, null, null, null, null, null, true
        ), owner.getId()))
                .isInstanceOf(PlatformWebhookAddressInUseException.class);
        assertThatThrownBy(() -> service.update(original.id(), new UpdatePlatformConnectionRequest(
                service.get(original.id()).version(),
                null, "not-a-valid-endpoint", null, null, null, null, null
        ), owner.getId()))
                .isInstanceOf(InvalidPlatformEndpointException.class);

        assertThat(eventsFor(original.id()))
                .extracting(PlatformConnectionEvent::getChangeType)
                .containsExactlyInAnyOrder(
                        PlatformConnectionChangeType.CREATED,
                        PlatformConnectionChangeType.PAUSED
                );
    }

    @Test
    @DisplayName("Should rename without changing the webhook address")
    void shouldRenameWithoutChangingWebhookAddress() {
        PlatformConnectionResponse created = createNamedShopify(
                "Northwind Supply",
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        PlatformConnectionResponse renamed = rename(created.id(), "Northwind Supply EU", created.version());

        assertThat(renamed.displayName()).isEqualTo("Northwind Supply EU");
        assertThat(renamed.slug()).isEqualTo("northwind-supply");
        assertThat(renamed.webhookPath()).isEqualTo("/api/v1/webhooks/orders/northwind-supply");
    }

    @Test
    @DisplayName("Should return not found for unknown connection identifiers")
    void shouldReturnNotFoundForUnknownConnection() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("RESOURCE_NOT_FOUND");
        assertThatThrownBy(() -> service.update(
                missing,
                new UpdatePlatformConnectionRequest(0L, null, null, null, null, null, null, true),
                owner.getId()
        ))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deactivate(missing, PAUSE_REASON, owner.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should return the same connection when the same create request is submitted again")
    void shouldReturnTheSameConnectionForARepeatedCreateRequest() {
        String requestKey = "c1f4a7e2-8b3d-4f6a-9c1e-2d5b8a7f4e30";
        CreatePlatformConnectionRequest request = new CreatePlatformConnectionRequest(
                PlatformType.SHOPIFY,
                "Northwind Supply",
                null,
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN,
                null,
                null
        );

        PlatformConnectionCreationResult first = service.create(request, owner.getId(), requestKey);
        PlatformConnectionCreationResult retried = service.create(request, owner.getId(), requestKey);

        assertThat(first.replayed()).isFalse();
        assertThat(retried.replayed()).isTrue();
        assertThat(retried.connection().id()).isEqualTo(first.connection().id());
        assertThat(connectionRepository.count()).isEqualTo(1);
        assertThat(eventsFor(first.connection().id()))
                .extracting(PlatformConnectionEvent::getChangeType)
                .containsExactly(PlatformConnectionChangeType.CREATED);
    }

    @Test
    @DisplayName("Should reject a malformed creation request key before creating anything")
    void shouldRejectMalformedCreationRequestKey() {
        assertThatThrownBy(() -> service.create(
                new CreatePlatformConnectionRequest(
                        PlatformType.SHOPIFY,
                        "Northwind Supply",
                        null,
                        SHOPIFY_ENDPOINT,
                        SHOPIFY_WEBHOOK_SECRET,
                        SHOPIFY_ACCESS_TOKEN,
                        null,
                        null
                ),
                owner.getId(),
                "not a valid key"
        ))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");

        assertThat(connectionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Should start at version zero and advance on every save")
    void shouldAdvanceVersionOnEverySave() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThat(created.version()).isZero();

        PlatformConnectionResponse renamed = rename(
                created.id(),
                "Northwind Supply EU",
                created.version()
        );

        assertThat(renamed.version()).isEqualTo(1);
        assertThat(service.get(created.id()).version()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should refuse a save built on an older version and leave the connection untouched")
    void shouldRefuseStaleSaves() {
        PlatformConnectionResponse created = createNamedShopify(
                "Northwind Supply",
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );
        PlatformConnectionResponse renamed = rename(
                created.id(),
                "Northwind Supply EU",
                created.version()
        );

        assertThatThrownBy(() -> rename(
                created.id(),
                "Northwind Supply Archive",
                created.version()
        ))
                .isInstanceOf(ConcurrentChangeException.class)
                .hasMessageContaining("changed by someone else")
                .extracting("errorCode")
                .isEqualTo("CONCURRENT_CHANGE");

        PlatformConnectionResponse current = service.get(created.id());
        assertThat(current.displayName()).isEqualTo("Northwind Supply EU");
        assertThat(current.version()).isEqualTo(renamed.version());
        assertThat(eventsFor(created.id()))
                .extracting(PlatformConnectionEvent::getChangeType)
                .containsExactly(
                        PlatformConnectionChangeType.CREATED,
                        PlatformConnectionChangeType.UPDATED
                );
    }

    @Test
    @DisplayName("Should require a version on every edit")
    void shouldRequireAVersionOnEdits() {
        PlatformConnectionResponse created = createShopify(
                SHOPIFY_ENDPOINT,
                SHOPIFY_WEBHOOK_SECRET,
                SHOPIFY_ACCESS_TOKEN
        );

        assertThatThrownBy(() -> service.update(
                created.id(),
                new UpdatePlatformConnectionRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                owner.getId()
        ))
                .isInstanceOf(InvalidPlatformVersionException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_PLATFORM_VERSION");
    }

    private List<PlatformConnectionEvent> eventsFor(UUID connectionId) {
        return eventRepository.findAll().stream()
                .filter(event -> event.getPlatformConnectionId().equals(connectionId))
                .toList();
    }

    private PlatformConnectionResponse rename(UUID connectionId, String displayName, long version) {
        return service.update(connectionId, new UpdatePlatformConnectionRequest(
                version, displayName, null, null, null, null, null, null
        ), owner.getId());
    }

    private PlatformConnectionResponse createShopify(
            String endpoint,
            String webhookSecret,
            String accessToken
    ) {
        return service.create(new CreatePlatformConnectionRequest(
                PlatformType.SHOPIFY,
                endpoint,
                webhookSecret,
                accessToken,
                null,
                null
        ), owner.getId());
    }

    private PlatformConnectionResponse createNamedShopify(
            String displayName,
            String endpoint,
            String webhookSecret,
            String accessToken
    ) {
        return service.create(new CreatePlatformConnectionRequest(
                PlatformType.SHOPIFY,
                displayName,
                null,
                endpoint,
                webhookSecret,
                accessToken,
                null,
                null
        ), owner.getId());
    }

    private PlatformConnectionResponse createWooCommerce() {
        return service.create(new CreatePlatformConnectionRequest(
                PlatformType.WOOCOMMERCE,
                WOOCOMMERCE_ENDPOINT,
                WOOCOMMERCE_WEBHOOK_SECRET,
                null,
                WOOCOMMERCE_CONSUMER_KEY,
                WOOCOMMERCE_CONSUMER_SECRET
        ), owner.getId());
    }
}
