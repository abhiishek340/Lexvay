package com.lexvay.rule.source;

/** A fetch that was refused or failed before an HTTP status existed: forbidden address, size cap, no connection. */
public class FetchRejected extends RuntimeException {

    public FetchRejected(String message) {
        super(message);
    }

    public FetchRejected(String message, Throwable cause) {
        super(message, cause);
    }
}
