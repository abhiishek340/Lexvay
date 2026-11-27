package com.lexvay.rule.source;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Keeps the server from being used to reach its own network (D-182). A rule source URL must be http or https and
 * every address its host resolves to must be a public one: loopback, link-local (which includes cloud metadata
 * services), private (RFC 1918), unique-local, carrier-grade NAT, "this network" and multicast addresses are
 * refused. Used when a source is registered and again on every hop of every fetch, because a redirect or a changed
 * DNS record can lead somewhere else than the address that was checked first.
 */
@Component
public class SsrfGuard implements UrlPolicy {

    private final HostResolver resolver;

    public SsrfGuard(HostResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void requireAllowed(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new FetchRejected("Only http and https addresses can be fetched");
        }
        String host = url.getHost();
        if (host == null || host.isBlank()) {
            throw new FetchRejected("The address has no host");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new FetchRejected("The host " + host + " cannot be resolved", e);
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new FetchRejected("The host " + host + " resolves to an internal address, which is not fetched");
            }
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0 || (first == 100 && (second & 0xC0) == 64);
        }
        if (((Inet6Address) address).isIPv4CompatibleAddress()) {
            return isInternal(embeddedIpv4(bytes));
        }
        return (bytes[0] & 0xFE) == 0xFC;
    }

    private static InetAddress embeddedIpv4(byte[] ipv6) {
        try {
            return InetAddress.getByAddress(new byte[] {ipv6[12], ipv6[13], ipv6[14], ipv6[15]});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
