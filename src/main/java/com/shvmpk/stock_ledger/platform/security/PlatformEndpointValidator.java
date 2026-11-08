package com.shvmpk.stock_ledger.platform.security;

import com.shvmpk.stock_ledger.common.error.InvalidPlatformEndpointException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

@Component
@RequiredArgsConstructor
public class PlatformEndpointValidator {

    private final PlatformCredentialProperties properties;

    public String validateAndNormalize(String rawEndpoint) {
        if (rawEndpoint == null || rawEndpoint.isBlank()) {
            throw invalid("Platform endpoint must not be blank.");
        }

        URI uri;
        try {
            uri = new URI(rawEndpoint.trim());
        } catch (URISyntaxException ex) {
            throw invalid("Platform endpoint must be a valid absolute URL.");
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw invalid("Platform endpoint must use HTTP or HTTPS.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw invalid("Platform endpoint must include a host.");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("Platform endpoint must not include user information.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid("Platform endpoint must not include a query string or fragment.");
        }

        if (!properties.allowInsecureEndpoints()) {
            if (!scheme.equalsIgnoreCase("https")) {
                throw invalid("Platform endpoint must use HTTPS in production.");
            }
            if (isPrivateOrLocalHost(host)) {
                throw invalid("Platform endpoint must not target a private or local address.");
            }
        }

        return stripTrailingSlashes(uri.toString());
    }

    private boolean isPrivateOrLocalHost(String host) {
        String bareHost = host;
        if (bareHost.startsWith("[") && bareHost.endsWith("]")) {
            bareHost = bareHost.substring(1, bareHost.length() - 1);
        }

        if ("localhost".equalsIgnoreCase(bareHost)) {
            return true;
        }
        if (!isIpLiteral(bareHost)) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(bareHost);
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return true;
            }

            byte[] bytes = address.getAddress();
            if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            return bytes.length == 4
                    && (bytes[0] & 0xff) == 255
                    && (bytes[1] & 0xff) == 255
                    && (bytes[2] & 0xff) == 255
                    && (bytes[3] & 0xff) == 255;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private boolean isIpLiteral(String host) {
        if (host.contains(":")) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private String stripTrailingSlashes(String endpoint) {
        int end = endpoint.length();
        while (end > 0 && endpoint.charAt(end - 1) == '/') {
            end--;
        }
        return endpoint.substring(0, end);
    }

    private InvalidPlatformEndpointException invalid(String message) {
        return new InvalidPlatformEndpointException(message);
    }
}
