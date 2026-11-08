package com.shvmpk.stock_ledger.user.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Authentication and credential-lifecycle counters. Tags are deliberately
 * limited to low-cardinality values; emails and user ids never belong here.
 */
@Component
public class AuthMetrics {

    private static final String LOGIN_METRIC = "stockledger.auth.login";
    private static final String PASSWORD_CHANGE_METRIC = "stockledger.auth.password.change";
    private static final String PASSWORD_RESET_METRIC = "stockledger.auth.password.reset";
    private static final String AUDIT_WRITE_FAILURE_METRIC = "stockledger.auth.audit.write_failure";

    private final MeterRegistry meterRegistry;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void loginSucceeded() {
        loginCounter("success").increment();
    }

    public void loginFailed() {
        loginCounter("failure").increment();
    }

    public void passwordChanged() {
        counter(PASSWORD_CHANGE_METRIC).increment();
    }

    public void passwordReset() {
        counter(PASSWORD_RESET_METRIC).increment();
    }

    public void auditWriteFailed() {
        counter(AUDIT_WRITE_FAILURE_METRIC).increment();
    }

    private Counter loginCounter(String outcome) {
        return Counter.builder(LOGIN_METRIC)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private Counter counter(String name) {
        return Counter.builder(name).register(meterRegistry);
    }
}
