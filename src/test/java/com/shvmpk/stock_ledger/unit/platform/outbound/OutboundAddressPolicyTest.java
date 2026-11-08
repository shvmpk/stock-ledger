package com.shvmpk.stock_ledger.unit.platform.outbound;

import com.shvmpk.stock_ledger.platform.outbound.BlockedOutboundAddressException;
import com.shvmpk.stock_ledger.platform.outbound.OutboundAddressPolicy;
import com.shvmpk.stock_ledger.platform.outbound.PlatformOutboundException;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Outbound Address Policy Unit Tests")
class OutboundAddressPolicyTest {

    private static final String KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";

    @Test
    @DisplayName("Should refuse a hostname that resolves to a private network address")
    void shouldRefusePrivateAddress() throws UnknownHostException {
        OutboundAddressPolicy policy = strictPolicy();

        assertThatThrownBy(() -> policy.validateResolved(
                "internal.northwind-supply.co",
                new InetAddress[]{InetAddress.getByName("10.24.8.17")}
        ))
                .isInstanceOf(BlockedOutboundAddressException.class)
                .hasMessageContaining("private or local");
    }

    @Test
    @DisplayName("Should refuse loopback, link-local, multicast and IPv6 unique-local targets")
    void shouldRefuseSpecialPurposeAddresses() throws UnknownHostException {
        OutboundAddressPolicy policy = strictPolicy();

        for (String address : new String[]{
                "127.0.0.1",
                "169.254.10.20",
                "224.0.0.1",
                "::1",
                "fd00:8a3f:2c11::5",
                "100.64.12.9",
                "198.18.4.2"
        }) {
            assertThatThrownBy(() -> policy.validateResolved(
                    "shop.example.com",
                    new InetAddress[]{InetAddress.getByName(address)}
            ))
                    .as("address %s", address)
                    .isInstanceOf(BlockedOutboundAddressException.class);
        }
    }

    @Test
    @DisplayName("Should refuse a hostname when any resolved destination is private")
    void shouldRefuseWhenAnyAddressIsPrivate() throws UnknownHostException {
        OutboundAddressPolicy policy = strictPolicy();

        assertThatThrownBy(() -> policy.validateResolved(
                "shop.example.com",
                new InetAddress[]{
                        InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("192.168.1.40")
                }
        )).isInstanceOf(BlockedOutboundAddressException.class);
    }

    @Test
    @DisplayName("Should accept a public destination")
    void shouldAcceptPublicAddress() throws UnknownHostException {
        OutboundAddressPolicy policy = strictPolicy();

        assertThatCode(() -> policy.validateResolved(
                "northwind-supply.myshopify.com",
                new InetAddress[]{InetAddress.getByName("93.184.216.34")}
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should refuse plain HTTP unless insecure endpoints are explicitly allowed")
    void shouldRefusePlainHttpInProduction() {
        OutboundAddressPolicy policy = strictPolicy();

        assertThatThrownBy(() -> policy.validateScheme(URI.create("http://northwind-supply.myshopify.com")))
                .isInstanceOf(PlatformOutboundException.class)
                .hasMessageContaining("HTTPS");

        assertThatCode(() -> policy.validateScheme(URI.create("https://northwind-supply.myshopify.com")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should allow loopback and plain HTTP only when the test escape hatch is on")
    void shouldAllowLocalTargetsWhenInsecureEndpointsAreAllowed() throws UnknownHostException {
        OutboundAddressPolicy policy = new OutboundAddressPolicy(
                new PlatformCredentialProperties(KEY, true)
        );

        assertThatCode(() -> policy.validateScheme(URI.create("http://localhost:18080"))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validateResolved(
                "localhost",
                new InetAddress[]{InetAddress.getByName("127.0.0.1")}
        )).doesNotThrowAnyException();
    }

    private OutboundAddressPolicy strictPolicy() {
        return new OutboundAddressPolicy(new PlatformCredentialProperties(KEY, false));
    }
}
