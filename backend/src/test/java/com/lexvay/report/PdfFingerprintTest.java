package com.lexvay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.document.DocumentService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PdfFingerprintTest {

    @Test
    void theStreamedHashEqualsTheHashOfTheWholeFileAcrossManyBuffers() {
        byte[] file = new byte[300 * 1024 + 7];
        new Random(14).nextBytes(file);
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, file, 0, 8);

        assertThat(PdfFingerprint.of(new ByteArrayInputStream(file))).isEqualTo(DocumentService.sha256(file));
    }

    @Test
    void onlyABodyStartingWithThePdfMagicIsHashed() {
        assertThat(PdfFingerprint.of(new ByteArrayInputStream("%PDF-".getBytes(StandardCharsets.US_ASCII))))
                .isEqualTo(DocumentService.sha256("%PDF-".getBytes(StandardCharsets.US_ASCII)));
        for (String body : new String[] {"", "%PDF", "%pdf-1.7", " %PDF-1.7", "PK\u0003\u0004"}) {
            assertThatThrownBy(() -> PdfFingerprint.of(new ByteArrayInputStream(body.getBytes(StandardCharsets.ISO_8859_1))))
                    .as(body).isInstanceOf(IllegalArgumentException.class).hasMessage("Not a PDF file");
        }
    }
}
