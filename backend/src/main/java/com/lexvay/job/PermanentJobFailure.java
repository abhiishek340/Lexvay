package com.lexvay.job;

/** A failure that retrying cannot fix (bad input, unparsable file). The job fails on the first attempt. */
public class PermanentJobFailure extends RuntimeException {
    public PermanentJobFailure(String message) {
        super(message);
    }

    public PermanentJobFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
