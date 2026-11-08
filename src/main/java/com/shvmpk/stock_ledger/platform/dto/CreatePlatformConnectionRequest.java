package com.shvmpk.stock_ledger.platform.dto;

import com.shvmpk.stock_ledger.platform.entity.PlatformType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlatformConnectionRequest(
        @NotNull(message = "Platform type is required.")
        PlatformType platformType,

        @Size(max = 100, message = "Display name must be at most 100 characters.")
        String displayName,

        @Size(max = 100, message = "Slug must be at most 100 characters.")
        @Pattern(
                regexp = "^[A-Za-z0-9]*(-[A-Za-z0-9]+)*$",
                message = "Webhook address may contain letters, numbers, and hyphens, "
                        + "and cannot start or end with a hyphen."
        )
        String slug,

        @NotBlank(message = "API endpoint is required.")
        @Size(max = 255, message = "API endpoint must be at most 255 characters.")
        String apiEndpoint,

        @NotBlank(message = "Webhook secret is required.")
        @Size(min = 16, max = 512, message = "Webhook secret must be between 16 and 512 characters.")
        String webhookSecret,

        @Size(max = 512, message = "Access token must be at most 512 characters.")
        String accessToken,

        @Size(max = 512, message = "Consumer key must be at most 512 characters.")
        String consumerKey,

        @Size(max = 512, message = "Consumer secret must be at most 512 characters.")
        String consumerSecret
) {
    public CreatePlatformConnectionRequest(
            PlatformType platformType,
            String apiEndpoint,
            String webhookSecret,
            String accessToken,
            String consumerKey,
            String consumerSecret
    ) {
        this(platformType, null, null, apiEndpoint, webhookSecret, accessToken, consumerKey, consumerSecret);
    }
}
