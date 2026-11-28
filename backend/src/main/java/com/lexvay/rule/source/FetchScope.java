package com.lexvay.rule.source;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Where a source may be fetched from: the hosts, and, when any path prefix is listed, the paths. Every hop of a fetch,
 * the first address and each redirect, must be within it (RG-2, D-567).
 */
public record FetchScope(List<String> allowedHosts, List<String> allowedPathPrefixes) {

    /** Only this host, any path: the scope of a robots.txt request. */
    public static FetchScope hostOnly(String host) {
        return new FetchScope(List.of(host), List.of());
    }

    /** The host of {@code url} as stored and compared: lower case, brackets kept on an IPv6 literal. */
    public static String hostOf(URI url) {
        return url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
    }

    /** @throws FetchRejected when the host or the path of {@code url} is not allowed */
    public void requireWithin(URI url) {
        String host = hostOf(url);
        if (!allowedHosts.contains(host)) {
            throw new FetchRejected("The address is on " + host + ", which is not one of the allowed hosts of this source");
        }
        String path = path(url);
        if (!allowedPathPrefixes.isEmpty() && allowedPathPrefixes.stream().noneMatch(path::startsWith)) {
            throw new FetchRejected("The path " + path + " is outside the allowed paths of this source");
        }
    }

    /** The path as the server will see it, with {@code ./} and {@code ../} resolved, so that a prefix cannot be walked out of. */
    static String path(URI url) {
        String path = url.normalize().getPath();
        return path == null || path.isEmpty() ? "/" : path;
    }
}
