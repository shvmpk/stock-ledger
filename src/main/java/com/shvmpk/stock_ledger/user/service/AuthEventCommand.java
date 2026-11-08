package com.shvmpk.stock_ledger.user.service;

import com.shvmpk.stock_ledger.user.entity.AuthEventType;
import com.shvmpk.stock_ledger.user.entity.AuthFailureReason;

import java.util.Map;
import java.util.UUID;

/**
 * Internal command object for the security audit trail. Kept as a record with a
 * canonical constructor so production code uses the typed recorder helpers
 * instead of assembling event fields ad hoc.
 */
public record AuthEventCommand(
        AuthEventType eventType,
        AuthFailureReason failureReason,
        UUID actorUserId,
        String actorEmail,
        UUID subjectUserId,
        String subjectEmail,
        Map<String, String> details
) {
}
