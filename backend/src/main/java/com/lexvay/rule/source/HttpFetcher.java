package com.lexvay.rule.source;

import java.net.URI;
import java.time.Duration;

/**
 * Fetches the page or file a rule source points at. The one seam between the monitoring job and the network:
 * production uses {@link JdkHttpFetcher}, tests script a fake by URL, so no test touches the network (D-180).
 */
public interface HttpFetcher {

    /** What to fetch and the limits of where the fetch may go: the first address and every redirect hop must be within {@code scope}. */
    record FetchRequest(URI url, FetchScope scope, Validators validators) {
    }

    /**
     * A completed HTTP exchange, whatever its status. {@code validators} are the ones the server sent and
     * {@code retryAfter} the wait it asked for with a 429 or 503, each null when it sent none.
     */
    record Fetched(int status, String contentType, byte[] body, Validators validators, Duration retryAfter) {
    }

    /**
     * Fetches the address of {@code request}, conditionally when it carries validators. A status of 400 or more, and
     * 304, are returned, not thrown, so the check can record them.
     *
     * @throws FetchRejected when an address is not allowed, the body is too large or the server cannot be reached
     */
    Fetched fetch(FetchRequest request);
}
