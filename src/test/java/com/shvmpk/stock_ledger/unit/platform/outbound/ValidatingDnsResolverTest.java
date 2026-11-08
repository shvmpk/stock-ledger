package com.shvmpk.stock_ledger.unit.platform.outbound;

import com.shvmpk.stock_ledger.platform.outbound.BlockedOutboundAddressException;
import com.shvmpk.stock_ledger.platform.outbound.OutboundAddressPolicy;
import com.shvmpk.stock_ledger.platform.outbound.ValidatingDnsResolver;
import com.shvmpk.stock_ledger.platform.security.PlatformCredentialProperties;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Validating DNS Resolver Unit Tests")
class ValidatingDnsResolverTest {

    private static final String KEY = "bf932A02/VQNtKfYxSaasAJGsal1yBYmUMLpZI0jj04=";

    @Test
    @DisplayName("Should refuse a hostname whose resolved address is private")
    void shouldRefuseHostnameResolvingToPrivateAddress() {
        ValidatingDnsResolver resolver = resolverReturning("10.0.0.5");

        assertThatThrownBy(() -> resolver.resolve("internal.northwind-supply.co"))
                .isInstanceOf(BlockedOutboundAddressException.class)
                .hasMessageContaining("private or local");
    }

    @Test
    @DisplayName("Should return the checked addresses unchanged when they are public")
    void shouldReturnCheckedPublicAddresses() throws UnknownHostException {
        ValidatingDnsResolver resolver = resolverReturning("93.184.216.34");

        InetAddress[] resolved = resolver.resolve("northwind-supply.myshopify.com");

        assertThat(resolved).hasSize(1);
        assertThat(resolved[0].getHostAddress()).isEqualTo("93.184.216.34");
    }

    @Test
    @DisplayName("Should preserve an unknown host failure instead of turning it into a block")
    void shouldPropagateUnknownHost() {
        DnsResolver unknown = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                throw new UnknownHostException(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                throw new UnknownHostException(host);
            }
        };
        ValidatingDnsResolver resolver = new ValidatingDnsResolver(strictPolicy(), unknown);

        assertThatThrownBy(() -> resolver.resolve("missing.northwind-supply.co"))
                .isInstanceOf(UnknownHostException.class);
    }

    private ValidatingDnsResolver resolverReturning(String address) {
        DnsResolver delegate = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName(address)};
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
        return new ValidatingDnsResolver(strictPolicy(), delegate);
    }

    private OutboundAddressPolicy strictPolicy() {
        return new OutboundAddressPolicy(new PlatformCredentialProperties(KEY, false));
    }
}
