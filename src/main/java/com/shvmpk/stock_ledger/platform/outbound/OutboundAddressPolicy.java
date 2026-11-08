package com.shvmpk.stock_ledger.platform.outbound;

import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * The single safety policy for every outbound platform call. It refuses a
 * scheme the installation does not permit and refuses any resolved address
 * that belongs to a private, loopback, link-local, multicast, or other
 * special-purpose range.
 *
 * <p>The test escape hatch ({@code app.platform.allow-insecure-endpoints}) is
 * deliberately narrow: it exists so integration tests can call a local
 * WireMock server. Production runs with it off.</p>
 */
@Component
public class OutboundAddressPolicy {

    private final PlatformCredentialProperties properties;

    public OutboundAddressPolicy(PlatformCredentialProperties properties) {
        this.properties = properties;
    }

    public void validateScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new PlatformOutboundException("Platform address must use HTTP or HTTPS.");
        }
        if (!properties.allowInsecureEndpoints() && !scheme.equalsIgnoreCase("https")) {
            throw new PlatformOutboundException("Platform address must use HTTPS in production.");
        }
    }

    /**
     * Validates every address the hostname resolved to. Refusing any private
     * destination (not just the first) closes the door on a hostname that
     * answers with a public address once and an internal one later.
     */
    public void validateResolved(String host, InetAddress[] addresses) {
        if (properties.allowInsecureEndpoints()) {
            return;
        }
        if (addresses == null || addresses.length == 0) {
            throw new BlockedOutboundAddressException(
                    "Platform address " + safeHost(host) + " could not be resolved."
            );
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new BlockedOutboundAddressException(
                        "Platform address " + safeHost(host)
                                + " resolves to a private or local address and was refused."
                );
            }
        }
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;

            // 0.0.0.0/8 "this network"
            if (first == 0) {
                return true;
            }
            // 100.64.0.0/10 carrier-grade NAT
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 192.0.0.0/24 IETF protocol assignments
            if (first == 192 && second == 0 && third == 0) {
                return true;
            }
            // 192.0.2.0/24 documentation
            if (first == 192 && second == 0 && third == 2) {
                return true;
            }
            // 198.18.0.0/15 benchmarking
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
            // 198.51.100.0/24 documentation
            if (first == 198 && second == 51 && third == 100) {
                return true;
            }
            // 203.0.113.0/24 documentation
            if (first == 203 && second == 0 && third == 113) {
                return true;
            }
            // 240.0.0.0/4 reserved, including the broadcast address
            return first >= 240;
        }

        if (address instanceof Inet6Address && bytes.length == 16) {
            // fc00::/7 unique local
            if ((bytes[0] & 0xfe) == 0xfc) {
                return true;
            }
            // 2001:db8::/32 documentation
            return (bytes[0] & 0xff) == 0x20
                    && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d
                    && (bytes[3] & 0xff) == 0xb8;
        }

        return false;
    }

    private String safeHost(String host) {
        return host == null ? "(unknown host)" : host.toLowerCase(Locale.ROOT);
    }
}
