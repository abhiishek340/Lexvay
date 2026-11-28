package com.lexvay.rule.source;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;

/**
 * The {@link HostResolver} of every database test, so no test needs DNS. IP literals resolve to themselves (the JDK
 * does that without a lookup); a name resolves only when it is listed here.
 */
@Primary
public class FakeHostResolver implements HostResolver {

    /** A name that resolves to a public address. */
    public static final String PUBLIC_HOST = "rules.example.test";
    /** A second name that resolves to a public address, for tests that need two hosts. */
    public static final String OTHER_PUBLIC_HOST = "other.example.test";
    /** A name that resolves to a private (RFC 1918) address. */
    public static final String PRIVATE_HOST = "internal.example.test";
    /** A name that resolves to the cloud metadata address. */
    public static final String METADATA_HOST = "metadata.example.test";

    private final Map<String, String> names = new ConcurrentHashMap<>(Map.of(
            PUBLIC_HOST, "203.0.113.10", OTHER_PUBLIC_HOST, "203.0.113.11", PRIVATE_HOST, "10.0.0.5", METADATA_HOST, "169.254.169.254"));

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (host.startsWith("[") || host.matches("[0-9.]+")) {
            return InetAddress.getAllByName(host);
        }
        String address = names.get(host);
        if (address == null) {
            throw new UnknownHostException(host);
        }
        return InetAddress.getAllByName(address);
    }
}
