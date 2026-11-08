package com.shvmpk.stock_ledger.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A platform connection edit. The version is the value the client read; the
 * server refuses the save when the connection has moved on since.
 */
public record UpdatePlatformConnectionRequest(
        @NotNull(message = "Version is required to save changes.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        Long version,

        @Size(max = 100, message = "Display name must be at most 100 characters.")
        @Pattern(regexp = ".*\\S.*", message = "Display name must not be blank.")
        String displayName,

        @Size(min = 1, max = 255, message = "API endpoint must be between 1 and 255 characters.")
        String apiEndpoint,

        @Size(max = 512, message = "Webhook secret must be at most 512 characters.")
        String webhookSecret,

        @Size(max = 512, message = "Access token must be at most 512 characters.")
        String accessToken,

        @Size(max = 512, message = "Consumer key must be at most 512 characters.")
        String consumerKey,

        @Size(max = 512, message = "Consumer secret must be at most 512 characters.")
        String consumerSecret,

        Boolean active,

        @Size(max = 500, message = "Pause reason must be at most 500 characters.")
        String pauseReason
) {
    /**
     * Kept for callers written before pausing required a reason. A pause
     * through this constructor is refused by the service until a reason is
     * supplied.
     */
    public UpdatePlatformConnectionRequest(
            Long version,
            String displayName,
            String apiEndpoint,
            String webhookSecret,
            String accessToken,
            String consumerKey,
            String consumerSecret,
            Boolean active
    ) {
        this(
                version,
                displayName,
                apiEndpoint,
                webhookSecret,
                accessToken,
                consumerKey,
                consumerSecret,
                active,
                null
        );
    }

    public boolean hasAnyField() {
        return displayName != null
                || apiEndpoint != null
                || webhookSecret != null
                || accessToken != null
                || consumerKey != null
                || consumerSecret != null
                || active != null;
    }
}
