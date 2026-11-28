package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.document.TestDocuments;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Product rule 4 for scanned pages (F11): evidence cited from a page that OCR read with low confidence is AMBIGUOUS
 * even if the model said EVIDENCE_FOUND, and the rationale tells the reviewer to check the original. The quote is
 * still verified against the stored OCR text exactly as against a text layer (product rule 2).
 */
class Rule4_LowOcrConfidenceForcesAmbiguousTest extends AbstractAssessmentTest {

    static final String LONG_QUOTE = "This required course covers the ACA Code of Ethics, legal standards, confidentiality, duty to warn";

    @Autowired JdbcClient jdbc;

    private JsonNode mappingFromScan(double confidence, boolean courseRequired, String quote, String finding) {
        ocr.enable().page(SYLLABUS_605, confidence);
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", courseRequired);
        UUID scan = fx.scannedDocument(t, programId, "coun605-scan.pdf", "SYLLABUS", cv,
                TestDocuments.scannedPdf(List.of(List.of("scanned page")), 72));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah ocr", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput(finding, scan, 1, quote, "Names the code."));
        return fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");
    }

    @Test
    void lowConfidencePageDowngradesFoundToAmbiguous() {
        var m = mappingFromScan(62.7, true, LONG_QUOTE, "EVIDENCE_FOUND");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString())
                .contains("Downgraded to ambiguous: the cited page was read by OCR with 62 % confidence; check the original");
        assertThat(m.get("evidence").get("quote").asString()).isEqualTo(LONG_QUOTE);
        assertThat(m.get("evidence").get("textSource").asString()).isEqualTo("OCR");
        assertThat(m.get("evidence").get("ocrConfidence").asDouble()).isEqualTo(62.7);
    }

    @Test
    void percentageIsRoundedDownSoItNeverShowsTheThreshold() {
        var m = mappingFromScan(79.96, true, LONG_QUOTE, "EVIDENCE_FOUND");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).contains("with 79 % confidence");
    }

    @Test
    void confidenceAtTheThresholdOrAboveKeepsEvidenceFound() {
        var atThreshold = mappingFromScan(80.0, true, LONG_QUOTE, "EVIDENCE_FOUND");
        assertThat(atThreshold.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(atThreshold.get("evidence").get("textSource").asString()).isEqualTo("OCR");
        assertThat(atThreshold.get("evidence").get("ocrConfidence").asDouble()).isEqualTo(80.0);
    }

    @Test
    void textLayerEvidenceHasNoOcrConfidence() {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah layer", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, LONG_QUOTE, "Names the code."));
        var m = fx.mappingFor(fx.mappings(t, fx.runAssessment(t, programId), "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(m.get("evidence").get("textSource").asString()).isEqualTo("TEXT_LAYER");
        assertThat(m.get("evidence").get("ocrConfidence").isNull()).isTrue();
    }

    @Test
    void everyApplicableReasonIsStated() {
        var m = mappingFromScan(55.0, false, "ACA Code of Ethics", "EVIDENCE_FOUND");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).contains("the program's graduation paths are not fully known (Electives: minimum not recorded)")
                .contains("shorter than 40 characters").contains("read by OCR with 55 % confidence");
    }

    @Test
    void ambiguousStaysAmbiguousAndOnlyFoundIsDowngraded() {
        var m = mappingFromScan(40.0, true, LONG_QUOTE, "AMBIGUOUS");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).doesNotContain("Downgraded");
    }

    @Test
    void aQuoteThatIsNotInTheOcrTextIsDiscardedWhateverTheConfidence() {
        var m = mappingFromScan(99.0, true, "This course covers research methods and program evaluation in depth.", "EVIDENCE_FOUND");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(m.get("rationale").asString()).isEqualTo("No verifiable passage found");
        assertThat(m.get("evidence").isNull()).isTrue();
    }

    @Test
    void aQuoteFromOcrTextIsStoredAsTheExactSubstringOfThePage() {
        var m = mappingFromScan(95.0, true, "ACA  Code of\nEthics, legal standards, confidentiality, duty to warn", "EVIDENCE_FOUND");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        int start = m.get("evidence").get("charStart").asInt();
        int end = m.get("evidence").get("charEnd").asInt();
        assertThat(SYLLABUS_605.stripTrailing().substring(start, end)).isEqualTo(m.get("evidence").get("quote").asString())
                .startsWith("ACA Code of Ethics");
    }

    @Test
    void ocrPagesAreStoredWithSourceAndConfidence() {
        mappingFromScan(88.0, true, LONG_QUOTE, "EVIDENCE_FOUND");
        var row = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT text_source, ocr_confidence FROM document_page WHERE text_source = 'OCR'").query().singleRow());
        assertThat(row.get("text_source")).isEqualTo("OCR");
        assertThat(((Number) row.get("ocr_confidence")).doubleValue()).isEqualTo(88.0);
    }
}
