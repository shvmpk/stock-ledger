package com.shvmpk.stock_ledger.user.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * NIST 800-63B aligned password policy. There are deliberately no composition
 * rules: length, a common-password blocklist, and context checks do the work.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 12;
    static final int MAX_BCRYPT_BYTES = 72;
    static final int MIN_CONTEXT_TOKEN_LENGTH = 3;

    private static final String MIN_LENGTH_MESSAGE = "Password must be at least 12 characters.";
    private static final String MAX_BYTES_MESSAGE = "Password must be at most 72 bytes when UTF-8 encoded.";
    private static final String COMMON_PASSWORD_MESSAGE =
            "Password is too common. Choose a less predictable password.";
    private static final String CONTEXT_MESSAGE = "Password must not contain your name or email address.";
    private static final String WHITESPACE_MESSAGE = "Password must not start or end with a space.";

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private final Set<String> commonPasswords;

    @Autowired
    public PasswordPolicy(@Value("classpath:security/common-passwords.txt") Resource commonPasswordsResource) {
        this(loadCommonPasswords(commonPasswordsResource));
    }

    private PasswordPolicy(Collection<String> commonPasswords) {
        this.commonPasswords = Set.copyOf(commonPasswords);
    }

    public static PasswordPolicy withCommonPasswords(Collection<String> commonPasswords) {
        return new PasswordPolicy(commonPasswords.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .map(entry -> entry.strip().toLowerCase(Locale.ROOT))
                .toList());
    }

    public List<PasswordViolation> validate(String rawPassword, String email, String fullName) {
        String password = rawPassword == null ? "" : rawPassword;
        List<PasswordViolation> violations = new java.util.ArrayList<>();

        if (password.length() < MIN_LENGTH) {
            violations.add(new PasswordViolation(MIN_LENGTH_MESSAGE));
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            violations.add(new PasswordViolation(MAX_BYTES_MESSAGE));
        }

        if (commonPasswords.contains(password.strip().toLowerCase(Locale.ROOT))) {
            violations.add(new PasswordViolation(COMMON_PASSWORD_MESSAGE));
        }

        if (containsContextToken(password, email, fullName)) {
            violations.add(new PasswordViolation(CONTEXT_MESSAGE));
        }

        if (!password.equals(password.strip())) {
            violations.add(new PasswordViolation(WHITESPACE_MESSAGE));
        }

        return List.copyOf(violations);
    }

    private boolean containsContextToken(String password, String email, String fullName) {
        String normalizedPassword = normalize(password);
        Set<String> contextTokens = new LinkedHashSet<>();

        if (email != null && !email.isBlank()) {
            int atIndex = email.indexOf('@');
            String localPart = atIndex >= 0 ? email.substring(0, atIndex) : email;
            contextTokens.add(localPart);
            addTokens(contextTokens, localPart);
        }

        if (fullName != null && !fullName.isBlank()) {
            addTokens(contextTokens, fullName);
        }

        return contextTokens.stream()
                .map(this::normalize)
                .filter(token -> token.length() >= MIN_CONTEXT_TOKEN_LENGTH)
                .anyMatch(normalizedPassword::contains);
    }

    private void addTokens(Set<String> tokens, String value) {
        for (String token : NON_ALPHANUMERIC.split(value.toLowerCase(Locale.ROOT))) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static Set<String> loadCommonPasswords(Resource resource) {
        Set<String> entries = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String candidate = line.strip().toLowerCase(Locale.ROOT);
                if (!candidate.isEmpty()) {
                    entries.add(candidate);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load the common password blocklist from " + resource, exception);
        }
        if (entries.isEmpty()) {
            throw new IllegalStateException("The common password blocklist must not be empty.");
        }
        return Set.copyOf(entries);
    }
}
