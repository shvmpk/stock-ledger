package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;

import java.util.Arrays;
import java.util.List;

/**
 * Public description of one sales channel: what it is called, whether it can be connected,
 * which credentials it needs and how its webhooks are signed.
 *
 * <p>This is a projection of {@link PlatformType}; it carries no secret values.</p>
 */
public record PlatformCatalogEntryResponse(
        PlatformType platformType,
        String displayName,
        boolean supported,
        String unsupportedReason,
        boolean outboundSync,
        String webhookPath,
        String apiEndpointExample,
        List<CredentialFieldResponse> credentialFields,
        List<String> signatureHeaders
) {

    public record CredentialFieldResponse(String key, String label, boolean secret) {
        static CredentialFieldResponse from(PlatformType.CredentialField field) {
            return new CredentialFieldResponse(field.key(), field.label(), true);
        }
    }

    public static List<PlatformCatalogEntryResponse> catalogue() {
        return Arrays.stream(PlatformType.values()).map(PlatformCatalogEntryResponse::from).toList();
    }

    static PlatformCatalogEntryResponse from(PlatformType platformType) {
        return new PlatformCatalogEntryResponse(
                platformType,
                platformType.displayName(),
                platformType.supportsSetup(),
                platformType.unsupportedReason(),
                platformType.supportsOutboundSync(),
                platformType.webhookPath(),
                platformType.apiEndpointExample(),
                platformType.credentialFields().stream().map(CredentialFieldResponse::from).toList(),
                platformType.signatureHeaders()
        );
    }
}
