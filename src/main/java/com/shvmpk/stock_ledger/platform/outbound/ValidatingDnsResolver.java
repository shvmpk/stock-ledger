package com.shvmpk.stock_ledger.platform.outbound;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves a hostname once and validates every answer before the connection
 * is opened. The connection then uses those checked addresses, so a second
 * lookup cannot swap in an internal destination.
 */
@Component
public class ValidatingDnsResolver implements DnsResolver {

    private final OutboundAddressPolicy policy;
    private final DnsResolver delegate;

    @Autowired
    public ValidatingDnsResolver(OutboundAddressPolicy policy) {
        this(policy, new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                return InetAddress.getAllByName(host);
            }

            @Override
            public String resolveCanonicalHostname(String host) throws UnknownHostException {
                return InetAddress.getByName(host).getCanonicalHostName();
            }
        });
    }

    public ValidatingDnsResolver(OutboundAddressPolicy policy, DnsResolver delegate) {
        this.policy = policy;
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = delegate.resolve(host);
        policy.validateResolved(host, addresses);
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }
}
