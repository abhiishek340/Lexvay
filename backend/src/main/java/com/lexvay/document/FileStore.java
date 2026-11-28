package com.lexvay.document;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Immutable blob storage for uploaded files and generated reports. Keys are {@code tenantId/objectId}. The byte-array
 * methods are for what fits in memory (an upload, a report); {@link #put(String, Path)} and {@link #open} move files
 * of any size without holding them.
 */
public interface FileStore {

    void put(String key, byte[] content);

    /** Stores the content of {@code file}, which stays where it is: the caller deletes it. All or nothing: a failure leaves no object under the key. */
    void put(String key, Path file);

    byte[] get(String key);

    /** The content as a stream the caller closes. */
    InputStream open(String key);

    boolean exists(String key);
}
