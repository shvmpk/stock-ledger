package com.shvmpk.stock_ledger.unit.platform;

import com.shvmpk.stock_ledger.platform.entity.PlatformCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Platform Credentials Value Object Unit Tests")
class PlatformCredentialsTest {

    @Test
    @DisplayName("Should treat an empty bundle as having no credentials")
    void shouldTreatEmptyBundleAsAbsent() {
        PlatformCredentials empty = PlatformCredentials.empty();

        assertThat(empty.hasAny()).isFalse();
        assertThat(empty.accessToken()).isNull();
        assertThat(empty.consumerKey()).isNull();
        assertThat(empty.consumerSecret()).isNull();
    }

    @Test
    @DisplayName("Should merge only non-null patch fields")
    void shouldMergeOnlyNonNullFields() {
        PlatformCredentials existing = new PlatformCredentials(
                "dummy-shopify-access-token",
                "test-woocommerce-consumer-key",
                "dummy-woocommerce-secret"
        );

        PlatformCredentials merged = existing.merge(
                new PlatformCredentials("shpat_new_2e8b5c1f7a4d9360", null, null)
        );

        assertThat(merged.accessToken()).isEqualTo("shpat_new_2e8b5c1f7a4d9360");
        assertThat(merged.consumerKey()).isEqualTo("test-woocommerce-consumer-key");
        assertThat(merged.consumerSecret()).isEqualTo("dummy-woocommerce-secret");
    }

    @Test
    @DisplayName("Should leave credentials unchanged when the patch is null")
    void shouldHandleNullPatch() {
        PlatformCredentials existing = new PlatformCredentials("dummy-shopify-access-token", null, null);

        assertThat(existing.merge(null)).isSameAs(existing);
    }

    @Test
    @DisplayName("Should ignore blank values when deciding whether any credentials exist")
    void shouldIgnoreBlankValues() {
        assertThat(new PlatformCredentials("  ", null, "").hasAny()).isFalse();
        assertThat(new PlatformCredentials("dummy-shopify-access-token", null, null).hasAny()).isTrue();
    }
}
