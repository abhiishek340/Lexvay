package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ContentLimitsTest {

    final TextExtractor extractor = new TextExtractor(new NoOcrEngine(), 200);

    /** A DOCX-shaped archive whose entries have the given sizes: random bytes first, then zeros. */
    static byte[] archive(long randomBytes, long zeroBytes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] random = new byte[(int) randomBytes];
            new Random(42).nextBytes(random);
            zip.write(random);
            byte[] zeros = new byte[1 << 20];
            for (long left = zeroBytes; left > 0; left -= zeros.length) {
                zip.write(zeros, 0, (int) Math.min(left, zeros.length));
            }
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void anOrdinaryDocxPasses() {
        byte[] docx = TestDocuments.docx(List.of("Clinical Handbook", "Practicum: 100 hours. Internship: 600 hours."));
        assertThatCode(() -> ContentLimits.requireSafeDocx(docx)).doesNotThrowAnyException();
    }

    @Test
    void anArchiveThatInflatesMoreThanAHundredTimesItsSizeIsRejected() {
        byte[] bomb = archive(0, 30L << 20); // 30 MB of zeros: well under 50 MB, but ratio of several thousand
        assertThat(bomb.length).isLessThan(100_000);
        assertThatThrownBy(() -> ContentLimits.requireSafeDocx(bomb))
                .isInstanceOf(ContentLimits.ContentLimitException.class)
                .hasMessage("DOCX expands to more than 100 times its size");
    }

    @Test
    void anArchiveThatInflatesPastFiftyMegabytesIsRejectedEvenWithAModestRatio() {
        // 700 KB of incompressible bytes keep the ratio near 75, so only the size limit can reject this one.
        byte[] large = archive(700 * 1024, 51L << 20);
        assertThat((double) ((51L << 20) + 700 * 1024) / large.length).isLessThan(100);
        assertThatThrownBy(() -> ContentLimits.requireSafeDocx(large))
                .isInstanceOf(ContentLimits.ContentLimitException.class)
                .hasMessage("DOCX expands to more than 50 MB");
    }

    @Test
    void anArchiveJustUnderBothLimitsPasses() {
        byte[] fits = archive(700 * 1024, 40L << 20);
        assertThatCode(() -> ContentLimits.requireSafeDocx(fits)).doesNotThrowAnyException();
    }

    @Test
    void theLimitsCanBeTightenedForTheCheck() {
        byte[] docx = TestDocuments.docx(List.of("x".repeat(5000)));
        assertThatThrownBy(() -> ContentLimits.requireSafeDocx(docx, 1000, 100))
                .isInstanceOf(ContentLimits.ContentLimitException.class);
        assertThatThrownBy(() -> ContentLimits.requireSafeDocx(docx, 50L << 20, 1))
                .isInstanceOf(ContentLimits.ContentLimitException.class);
    }

    @Test
    void bytesThatAreNotAnArchiveAreLeftToTheParser() {
        assertThatCode(() -> ContentLimits.requireSafeDocx("PK\u0003\u0004 not really a zip file".getBytes()))
                .doesNotThrowAnyException();
    }

    @Test
    void theExtractorAppliesTheDocxGuardToo() {
        byte[] bomb = archive(0, 30L << 20);
        assertThatThrownBy(() -> extractor.extract(FileType.DOCX, bomb))
                .isInstanceOf(ContentLimits.ContentLimitException.class);
    }

    @Test
    void pdfsUpToFiveHundredPagesPassAndLongerOnesAreRejected() {
        assertThatCode(() -> ContentLimits.requirePdfPages(500)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ContentLimits.requirePdfPages(501))
                .isInstanceOf(ContentLimits.ContentLimitException.class)
                .hasMessage("PDF has 501 pages; the limit is 500");
    }

    @Test
    void theUploadCheckCountsThePagesOfAPdfWithoutExtractingIt() {
        byte[] atCap = TestDocuments.pdf(java.util.Collections.nCopies(500, List.of("Fictional. Prototype data.")));
        byte[] overCap = TestDocuments.pdf(java.util.Collections.nCopies(501, List.of("Fictional. Prototype data.")));

        assertThatCode(() -> ContentLimits.requirePdfPages(atCap)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ContentLimits.requirePdfPages(overCap))
                .isInstanceOf(ContentLimits.ContentLimitException.class)
                .hasMessage("PDF has 501 pages; the limit is 500");
    }

    @Test
    void aPdfTheUploadCheckCannotReadIsLeftToIngest() {
        assertThatCode(() -> ContentLimits.requirePdfPages(TestDocuments.corruptPdf())).doesNotThrowAnyException();
        assertThatCode(() -> ContentLimits.requirePdfPages("%PDF-1.7 not really a pdf".getBytes())).doesNotThrowAnyException();
    }

    @Test
    void theExtractorRejectsAPdfOverThePageCapWithItsOwnMessage() {
        byte[] pdf = TestDocuments.pdf(java.util.Collections.nCopies(501, List.of("Fictional. Prototype data.")));
        assertThatThrownBy(() -> extractor.extract(FileType.PDF, pdf))
                .isInstanceOf(ContentLimits.ContentLimitException.class)
                .hasMessage("PDF has 501 pages; the limit is 500");
    }

    @Test
    void theExtractorReadsAPdfOfExactlyTheCap() {
        byte[] pdf = TestDocuments.pdf(java.util.Collections.nCopies(500, List.of("Fictional. Prototype data.")));
        assertThat(extractor.extract(FileType.PDF, pdf)).hasSize(500);
    }
}
