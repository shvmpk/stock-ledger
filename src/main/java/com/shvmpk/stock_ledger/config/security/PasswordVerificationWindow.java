package com.shvmpk.stock_ledger.config.security;

import jakarta.servlet.http.HttpSession;

import java.time.Duration;
import java.time.Instant;

/**
 * Tracks when the current session last proved knowledge of the account
 * password. A forced change may omit {@code currentPassword} only while this
 * proof is fresh, so a hijacked session cannot silently rotate the credential
 * hours later.
 */
public final class PasswordVerificationWindow {

    public static final String SESSION_ATTRIBUTE = "passwordVerifiedAt";
    public static final Duration VALIDITY = Duration.ofMinutes(10);

    private PasswordVerificationWindow() {
    }

    public static void stamp(HttpSession session) {
        if (session != null) {
            session.setAttribute(SESSION_ATTRIBUTE, Instant.now());
        }
    }

    public static boolean isFresh(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(value instanceof Instant verifiedAt)) {
            return false;
        }
        return Duration.between(verifiedAt, Instant.now()).compareTo(VALIDITY) < 0;
    }
}
