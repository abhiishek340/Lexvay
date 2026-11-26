package com.lexvay.document;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 file store. Bucket-level versioning and encryption are configured in infrastructure, not here. */
public class S3FileStore implements FileStore {

    private final S3Client s3;
    private final String bucket;

    public S3FileStore(S3Client s3, String bucket) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("LEXVAY_S3_BUCKET must be set when LEXVAY_FILESTORE=s3");
        }
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
