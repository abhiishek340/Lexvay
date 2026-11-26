package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 2 end to end: a plausible but fabricated quote is discarded and no evidence span is stored. */
class Rule2_FabricatedQuoteDiscardedTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;

    @Test
    void fabricatedQuoteBecomesEvidenceMissing() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah fabricated", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"),
                AssessmentFixtures.subject("Confidentiality", "counseling coursework on confidentiality and duty to warn"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1,
                "This course covers research methods and program evaluation in depth.", "Looks like a match."));
        fake.script("MATCH:UT-2", fx.matchOutput("AMBIGUOUS", syllabus, 1, null, "Related but no quote."));

        UUID assessmentId = fx.runAssessment(t, programId);
        var mappings = fx.mappings(t, assessmentId, "UT");

        var fabricated = fx.mappingFor(mappings, "UT-1");
        assertThat(fabricated.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(fabricated.get("rationale").asString()).isEqualTo("No verifiable passage found");
        assertThat(fabricated.get("evidence").isNull()).isTrue();

        var noQuote = fx.mappingFor(mappings, "UT-2");
        assertThat(noQuote.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(noQuote.get("rationale").asString()).startsWith("Model output rejected").contains("requires document_id, page_number and a quote");

        Long spans = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT count(*) FROM evidence_span WHERE document_id = :d").param("d", syllabus).query(Long.class).single());
        assertThat(spans).isZero();
    }
}
