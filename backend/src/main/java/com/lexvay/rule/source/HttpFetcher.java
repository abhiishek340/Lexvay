package com.lexvay.rule.source;

import java.net.URI;

/**
 * Fetches the page or file a rule source points at. The one seam between the monitoring job and the network:
 * production uses {@link JdkHttpFetcher}, tests script a fake by URL, so no test touches the network (D-180).
 */
public interface HttpFetcher {

    /** A completed HTTP exchange, whatever its status. */
    record Fetched(int status, String contentType, byte[] body) {
    }

    /**
     * Fetches {@code url}. A status of 400 or more is returned, not thrown, so the check can record it.
     *
     * @throws FetchRejected when the address is not allowed, the body is too large or the server cannot be reached
     */
    Fetched fetch(URI url);
}
