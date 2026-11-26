package com.lexvay.document;

/** Immutable blob storage for uploaded files and generated reports. Keys are {@code tenantId/objectId}. */
public interface FileStore {

    void put(String key, byte[] content);

    byte[] get(String key);

    boolean exists(String key);
}
