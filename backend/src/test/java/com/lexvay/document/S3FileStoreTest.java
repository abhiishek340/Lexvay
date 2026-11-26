package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** The S3 client is a Mockito mock (D-042): no test talks to AWS. */
class S3FileStoreTest {

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
}
