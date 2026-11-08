package com.shvmpk.stock_ledger.unit.user;

import com.shvmpk.stock_ledger.user.service.AuthMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Auth Metrics Tests")
class AuthMetricsTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private SimpleMeterRegistry registry;
    private AuthMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AuthMetrics(registry);
    }

    @Test
    @DisplayName("Counts login successes and failures under the outcome tag")
    void shouldCountLoginOutcomes() {
        metrics.loginSucceeded();
        metrics.loginSucceeded();
        metrics.loginFailed();

        Counter success = registry.get("stockledger.auth.login").tag("outcome", "success").counter();
        Counter failure = registry.get("stockledger.auth.login").tag("outcome", "failure").counter();

        assertThat(success.count()).isEqualTo(2);
        assertThat(failure.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Counts password changes, resets, and audit write failures")
    void shouldCountPasswordLifecycleAndAuditFailures() {
        metrics.passwordChanged();
        metrics.passwordReset();
        metrics.passwordReset();
        metrics.auditWriteFailed();

        assertThat(registry.get("stockledger.auth.password.change").counter().count()).isEqualTo(1);
        assertThat(registry.get("stockledger.auth.password.reset").counter().count()).isEqualTo(2);
        assertThat(registry.get("stockledger.auth.audit.write_failure").counter().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Never uses an email or user id as a metric tag")
    void shouldNotExposeHighCardinalityTags() {
        metrics.loginSucceeded();
        metrics.loginFailed();
        metrics.passwordChanged();
        metrics.passwordReset();
        metrics.auditWriteFailed();

        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(tag.getKey()).isIn("outcome");
                assertThat(tag.getValue()).doesNotContain("@");
                assertThat(UUID_PATTERN.matcher(tag.getValue()).matches())
                        .as("Tag %s must not contain a user id", tag.getKey())
                        .isFalse();
            }
        }
    }
}
