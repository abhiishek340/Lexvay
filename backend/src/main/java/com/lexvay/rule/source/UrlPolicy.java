package com.lexvay.rule.source;

import java.net.URI;

/** Decides whether the server may connect to a URL. {@link SsrfGuard} is the production policy. */
public interface UrlPolicy {

    /** @throws FetchRejected when {@code url} must not be fetched */
    void requireAllowed(URI url);
}
