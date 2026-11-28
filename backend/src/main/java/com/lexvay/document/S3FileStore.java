package com.lexvay.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/** S3 file store. Bucket-level versioning and encryption are configured in infrastructure, not here. */
public class S3FileStore implements FileStore {

    /** A file above this size is uploaded in parts of this size, so at most one part is held in memory. */
    static final int PART_SIZE = 16 * 1024 * 1024;

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
    public void put(String key, Path file) {
        try {
            if (Files.size(file) <= PART_SIZE) {
                s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromFile(file));
            } else {
                putInParts(key, file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store " + key, e);
        }
    }

    /** Nothing exists under the key until the last part is in: a failure aborts the upload and its parts are discarded. */
    private void putInParts(String key, Path file) throws IOException {
        String uploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder().bucket(bucket).key(key).build()).uploadId();
        try (InputStream in = Files.newInputStream(file)) {
            List<CompletedPart> parts = new ArrayList<>();
            byte[] buffer = new byte[PART_SIZE];
            int read;
            while ((read = in.readNBytes(buffer, 0, PART_SIZE)) > 0) {
                int number = parts.size() + 1;
                String etag = s3.uploadPart(
                        UploadPartRequest.builder().bucket(bucket).key(key).uploadId(uploadId).partNumber(number).build(),
                        RequestBody.fromByteBuffer(ByteBuffer.wrap(buffer, 0, read))).eTag();
                parts.add(CompletedPart.builder().partNumber(number).eTag(etag).build());
            }
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()).build());
        } catch (IOException | RuntimeException e) {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(key).uploadId(uploadId).build());
            throw e;
        }
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
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
