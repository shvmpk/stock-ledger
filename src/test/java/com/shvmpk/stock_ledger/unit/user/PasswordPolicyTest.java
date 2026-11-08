package com.shvmpk.stock_ledger.unit.user;

import com.shvmpk.stock_ledger.user.service.PasswordPolicy;
import com.shvmpk.stock_ledger.user.service.PasswordViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Password Policy Unit Tests (NIST 800-63B aligned)")
class PasswordPolicyTest {

    private static final String EMAIL = "ingrid.bergstrom@nordicliving.se";
    private static final String FULL_NAME = "Ingrid Bergström";

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password",
            "qwertyuiop12",
            "letmein123456",
            "welcome12345678",
            "stockledger2026"
    );

    private final PasswordPolicy policy = PasswordPolicy.withCommonPasswords(COMMON_PASSWORDS);

    @Test
    @DisplayName("Rejects 11 characters and accepts exactly 12")
    void shouldEnforceMinimumLength() {
        assertThat(policy.validate("FjordLanter", EMAIL, FULL_NAME))
                .extracting(PasswordViolation::message)
                .containsExactly("Password must be at least 12 characters.");

        assertThat(policy.validate("FjordLantern", EMAIL, FULL_NAME)).isEmpty();
    }

    @Test
    @DisplayName("Rejects 73 UTF-8 bytes and accepts 72, including multibyte characters")
    void shouldEnforceBcryptByteBoundary() {
        assertThat(policy.validate("a".repeat(73), EMAIL, FULL_NAME))
                .extracting(PasswordViolation::message)
                .containsExactly("Password must be at most 72 bytes when UTF-8 encoded.");

        assertThat(policy.validate("a".repeat(72), EMAIL, FULL_NAME))
                .as("Exactly 72 bytes is the BCrypt limit and must be accepted")
                .isEmpty();

        String thirtySixAEszetts = "æ".repeat(36);
        assertThat(thirtySixAEszetts.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(policy.validate(thirtySixAEszetts, EMAIL, FULL_NAME))
                .as("72 multibyte characters worth of bytes must be accepted")
                .isEmpty();

        assertThat(policy.validate("æ".repeat(37), EMAIL, FULL_NAME))
                .as("74 multibyte bytes must be rejected even though the character count is below 72")
                .extracting(PasswordViolation::message)
                .containsExactly("Password must be at most 72 bytes when UTF-8 encoded.");
    }

    @Test
    @DisplayName("Rejects common passwords case-insensitively")
    void shouldRejectCommonPasswords() {
        assertThat(policy.validate("QWERTYUIOP12", EMAIL, FULL_NAME))
                .extracting(PasswordViolation::message)
                .containsExactly("Password is too common. Choose a less predictable password.");
    }

    @Test
    @DisplayName("Rejects a password containing the email local part or a full-name token")
    void shouldRejectContextualWords() {
        assertThat(policy.validate("Ingrid-Fjord-2026", EMAIL, FULL_NAME))
                .as("The email local part token 'ingrid' and the name token 'ingrid' both match")
                .extracting(PasswordViolation::message)
                .containsExactly("Password must not contain your name or email address.");

        assertThat(policy.validate("Eriksson-Warehouse-2026", "lars.eriksson@nordicliving.se", "Lars Eriksson"))
                .extracting(PasswordViolation::message)
                .containsExactly("Password must not contain your name or email address.");
    }

    @Test
    @DisplayName("Rejects leading or trailing whitespace but allows internal spaces")
    void shouldEnforceWhitespaceRules() {
        assertThat(policy.validate(" FjordLantern2026", EMAIL, FULL_NAME))
                .extracting(PasswordViolation::message)
                .containsExactly("Password must not start or end with a space.");

        assertThat(policy.validate("FjordLantern2026 ", EMAIL, FULL_NAME))
                .extracting(PasswordViolation::message)
                .containsExactly("Password must not start or end with a space.");

        assertThat(policy.validate("Fjord Lantern 2026", EMAIL, FULL_NAME))
                .as("Internal spaces are legitimate in passphrases")
                .isEmpty();
    }

    @Test
    @DisplayName("Accepts a strong 16+ character passphrase with no composition rules")
    void shouldAcceptStrongPassphrase() {
        assertThat(policy.validate("CopperHarborDawn42", EMAIL, FULL_NAME)).isEmpty();
        assertThat(policy.validate("quiet meadow lantern drift", EMAIL, FULL_NAME)).isEmpty();
    }

    @Test
    @DisplayName("Returns every violation together rather than stopping at the first")
    void shouldReturnAllViolations() {
        List<String> messages = policy.validate("password ", EMAIL, FULL_NAME).stream()
                .map(PasswordViolation::message)
                .toList();

        assertThat(messages).containsExactly(
                "Password must be at least 12 characters.",
                "Password is too common. Choose a less predictable password.",
                "Password must not start or end with a space."
        );
    }
}
