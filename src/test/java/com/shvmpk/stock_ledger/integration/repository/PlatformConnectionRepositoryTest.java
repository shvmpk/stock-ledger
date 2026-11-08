package com.shvmpk.stock_ledger.integration.repository;

import com.shvmpk.stock_ledger.TestcontainersConfiguration;
import com.shvmpk.stock_ledger.platform.entity.PlatformConnection;
import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import com.shvmpk.stock_ledger.platform.repository.PlatformConnectionRepository;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@DisplayName("Platform Connection Repository Integration Tests")
class PlatformConnectionRepositoryTest {

    @Autowired
    private PlatformConnectionRepository platformConnectionRepository;

    @Autowired
    private PlatformCredentialCipher credentialCipher;

    @BeforeEach
    void setUp() {
        platformConnectionRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Should find every active connection for a platform type")
    void shouldFindActiveConnectionsByType() {
        PlatformConnection closed = connection(
                "Northwind Supply (closed)",
                "https://northwind-supply-archive.myshopify.com",
                false
        );
        PlatformConnection northwind = connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com",
                true
        );
        PlatformConnection harbour = connection(
                "Harbour & Pine",
                "https://harbour-and-pine.myshopify.com",
                true
        );
        platformConnectionRepository.saveAll(List.of(closed, northwind, harbour));

        List<PlatformConnection> active =
                platformConnectionRepository.findByPlatformTypeAndActiveTrue(PlatformType.SHOPIFY);

        assertThat(active)
                .extracting(PlatformConnection::getId)
                .containsExactlyInAnyOrder(northwind.getId(), harbour.getId());
    }

    @Test
    @DisplayName("Should permit multiple inactive rows of the same platform type")
    void shouldPermitMultipleInactiveRows() {
        PlatformConnection legacy = connection(
                "Legacy Northwind",
                "https://legacy-northwind.myshopify.com",
                false
        );
        PlatformConnection replacement = connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com",
                false
        );
        platformConnectionRepository.saveAll(List.of(legacy, replacement));

        List<PlatformConnection> inactive =
                platformConnectionRepository.findByActive(false);

        assertThat(inactive)
                .extracting(PlatformConnection::getApiEndpoint)
                .containsExactlyInAnyOrder(
                        "https://legacy-northwind.myshopify.com",
                        "https://northwind-supply.myshopify.com"
                );
    }

    @Test
    @DisplayName("Should sort active connections first and then by platform type")
    void shouldSortActiveConnectionsFirst() {
        platformConnectionRepository.saveAll(List.of(
                connection("Legacy Northwind", "https://legacy-northwind.myshopify.com", false),
                connection(PlatformType.WOOCOMMERCE, "Northwind Home", "https://northwind-home.co.uk", true),
                connection("Northwind Supply", "https://northwind-supply.myshopify.com", true)
        ));

        List<PlatformConnection> sorted =
                platformConnectionRepository.findAllByOrderByActiveDescPlatformTypeAsc();

        assertThat(sorted)
                .extracting(PlatformConnection::getPlatformType)
                .containsExactly(PlatformType.SHOPIFY, PlatformType.WOOCOMMERCE, PlatformType.SHOPIFY);
        assertThat(sorted)
                .extracting(PlatformConnection::isActive)
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("Should allow two active stores of the same platform with different webhook addresses")
    void shouldAllowTwoActiveStoresOfTheSamePlatform() {
        platformConnectionRepository.saveAndFlush(connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com",
                true
        ));
        platformConnectionRepository.saveAndFlush(connection(
                "Harbour & Pine",
                "https://harbour-and-pine.myshopify.com",
                true
        ));

        assertThat(platformConnectionRepository.findByActive(true)).hasSize(2);
        assertThat(platformConnectionRepository.findBySlugAndActiveTrue("northwind-supply")).isPresent();
        assertThat(platformConnectionRepository.findBySlugAndActiveTrue("harbour-pine")).isPresent();
    }

    @Test
    @DisplayName("Should reject two active connections that share one webhook address")
    void shouldRejectDuplicateActiveWebhookAddress() {
        platformConnectionRepository.saveAndFlush(connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com",
                true
        ));

        assertThatThrownBy(() -> platformConnectionRepository.saveAndFlush(connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com/admin",
                true
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should detect another active connection using the same webhook address")
    void shouldDetectAnotherActiveConnectionWithTheSameSlug() {
        PlatformConnection active = connection(
                "Northwind Supply",
                "https://northwind-supply.myshopify.com",
                true
        );
        platformConnectionRepository.saveAndFlush(active);

        assertThat(platformConnectionRepository
                .existsBySlugAndActiveTrueAndIdNot("northwind-supply", UUID.randomUUID()))
                .isTrue();
        assertThat(platformConnectionRepository
                .existsBySlugAndActiveTrueAndIdNot("northwind-supply", active.getId()))
                .isFalse();
        assertThat(platformConnectionRepository
                .existsBySlugAndActiveTrueAndIdNot("harbour-pine", UUID.randomUUID()))
                .isFalse();
    }

    private PlatformConnection connection(String displayName, String endpoint, boolean active) {
        return connection(PlatformType.SHOPIFY, displayName, endpoint, active);
    }

    private PlatformConnection connection(
            PlatformType type,
            String displayName,
            String endpoint,
            boolean active
    ) {
        PlatformConnection connection = PlatformConnection.create(
                UUID.randomUUID(),
                type,
                displayName,
                null,
                endpoint,
                credentialCipher.encrypt("dummy-webhook-test-secret", "webhookSecret"),
                credentialCipher.encrypt(
                        "{\"accessToken\":\"dummy-shopify-test-credential\"}",
                        "apiCredentials"
                )
        );
        if (!active) {
            connection.pause("Store closed for a seasonal break.");
        }
        return connection;
    }
}
