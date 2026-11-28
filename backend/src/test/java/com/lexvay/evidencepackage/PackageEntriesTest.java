package com.lexvay.evidencepackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.evidencepackage.PackageEntries.CitedDocument;
import com.lexvay.evidencepackage.PackageSourceRepository.Quote;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackageEntriesTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private static Quote quote(UUID document, String filename, int page) {
        return new Quote("CA-1", document, filename, "t/" + document, "f".repeat(64), page, 0, 10, "a quote", "e".repeat(64));
    }

    @Test
    void aDocumentCitedManyTimesIsPackagedOnceInFilenameOrder() {
        List<CitedDocument> documents = PackageEntries.citedDocuments(List.of(quote(B, "syllabus.pdf", 1), quote(A, "plan.pdf", 1),
                quote(B, "syllabus.pdf", 2)));

        assertThat(documents).extracting(CitedDocument::path).containsExactly("documents/plan.pdf", "documents/syllabus.pdf");
        assertThat(documents).extracting(CitedDocument::id).containsExactly(A, B);
        assertThat(documents.get(0).storageKey()).isEqualTo("t/" + A);
        assertThat(documents.get(0).sha256()).isEqualTo("f".repeat(64));
    }

    @Test
    void twoDocumentsWithOneFilenameKeepDistinctPathsWhateverTheOrderOfTheQuotes() {
        List<Quote> quotes = List.of(quote(C, "syllabus.pdf", 1), quote(A, "syllabus.pdf", 1), quote(B, "syllabus.pdf", 1));
        List<CitedDocument> forward = PackageEntries.citedDocuments(quotes);
        List<CitedDocument> backward = PackageEntries.citedDocuments(quotes.reversed());

        assertThat(forward).extracting(CitedDocument::path)
                .containsExactly("documents/syllabus.pdf", "documents/bbbbbbbb-syllabus.pdf", "documents/cccccccc-syllabus.pdf");
        assertThat(backward).isEqualTo(forward);
    }

    @Test
    void aFilenameCannotLeaveTheDocumentsFolderOrCarryControlCharacters() {
        List<CitedDocument> documents = PackageEntries.citedDocuments(List.of(quote(A, "../..\\evil\n.pdf", 1)));

        assertThat(documents.get(0).path()).isEqualTo("documents/.._.._evil_.pdf");
    }

    @Test
    void aProgramNameBecomesALowerCaseSlug() {
        assertThat(PackageService.slug("M.S. Clinical Mental Health Counseling")).isEqualTo("m-s-clinical-mental-health-counseling");
        assertThat(PackageService.slug("  Ünïcode & Co.  ")).isEqualTo("n-code-co");
        assertThat(PackageService.slug("***")).isEqualTo("program");
    }
}
