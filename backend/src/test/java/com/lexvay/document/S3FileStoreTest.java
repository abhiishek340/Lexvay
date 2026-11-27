package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/** The S3 client is a Mockito mock (D-042): no test talks to AWS. */
class S3FileStoreTest {

    @TempDir Path dir;

    S3Client s3 = mock(S3Client.class);
    S3FileStore store = new S3FileStore(s3, "lexvay-files");

    @Test
    void putUsesBucketAndKey() {
        store.put("t1/d1", new byte[] {7, 8});
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo("lexvay-files");
        assertThat(request.getValue().key()).isEqualTo("t1/d1");
        assertThat(body.getValue().optionalContentLength()).hasValue(2L);
    }

    @Test
    void getReadsBytes() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9}));
        assertThat(store.get("t1/d1")).containsExactly(9);
    }

    @Test
    void existsMapsNoSuchKeyToFalse() {
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        assertThat(store.exists("t1/missing")).isFalse();
    }

    @Test
    void bucketIsRequired() {
        assertThatThrownBy(() -> new S3FileStore(s3, "")).isInstanceOf(IllegalArgumentException.class);
    }

    /** A file of {@code size} bytes without writing them: the unwritten bytes read as zeros. */
    private Path fileOf(long size) throws IOException {
        Path file = dir.resolve("package-" + size + ".zip");
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(size);
        }
        return file;
    }

    @Test
    void aFileUpToOnePartIsOneRequestReadFromTheFile() throws IOException {
        store.put("t1/p1", fileOf(S3FileStore.PART_SIZE));

        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(any(PutObjectRequest.class), body.capture());
        assertThat(body.getValue().optionalContentLength()).hasValue((long) S3FileStore.PART_SIZE);
        verify(s3, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void aLargerFileIsUploadedInPartsAndCompletedOnce() throws IOException {
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(CreateMultipartUploadResponse.builder().uploadId("up-1").build());
        when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag-1").build(), UploadPartResponse.builder().eTag("etag-2").build());

        store.put("t1/p2", fileOf(S3FileStore.PART_SIZE + 5L));

        var requests = ArgumentCaptor.forClass(UploadPartRequest.class);
        var bodies = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3, times(2)).uploadPart(requests.capture(), bodies.capture());
        assertThat(requests.getAllValues()).extracting(UploadPartRequest::partNumber).containsExactly(1, 2);
        assertThat(requests.getAllValues()).allSatisfy(r -> assertThat(r.uploadId()).isEqualTo("up-1"));
        assertThat(bodies.getAllValues()).extracting(b -> b.optionalContentLength().orElseThrow()).containsExactly((long) S3FileStore.PART_SIZE, 5L);
        var complete = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(s3).completeMultipartUpload(complete.capture());
        assertThat(complete.getValue().key()).isEqualTo("t1/p2");
        assertThat(complete.getValue().multipartUpload().parts()).extracting(p -> p.eTag()).containsExactly("etag-1", "etag-2");
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    @Test
    void aFailedPartAbortsTheUploadSoNoObjectAppears() throws IOException {
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class))).thenReturn(CreateMultipartUploadResponse.builder().uploadId("up-2").build());
        when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("etag-1").build())
                .thenThrow(new IllegalStateException("connection reset"));
        Path file = fileOf(S3FileStore.PART_SIZE + 5L);

        assertThatThrownBy(() -> store.put("t1/p3", file)).isInstanceOf(IllegalStateException.class).hasMessage("connection reset");

        var abort = ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(s3).abortMultipartUpload(abort.capture());
        assertThat(abort.getValue().uploadId()).isEqualTo("up-2");
        verify(s3, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    @Test
    void openStreamsTheObject() throws IOException {
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(new byte[] {1, 2, 3}))));

        try (var in = store.open("t1/p4")) {
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3);
        }
        var request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3).getObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("lexvay-files");
        assertThat(request.getValue().key()).isEqualTo("t1/p4");
    }
}
