package com.lexvay.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProgramFactApiTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;

    @Test
    void verifiedQuoteBecomesEvidenceAndIsAudited() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);

        var created = fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "requires  a total of 60 semester credit hours");
        assertThat(created).hasStatus(201);
        var fact = fx.read(created);
        assertThat(fact.get("quote").asString()).isEqualTo("requires a total of 60 semester credit hours");
        assertThat(fact.get("current").asBoolean()).isTrue();
        assertThat(fact.get("filename").asString()).isEqualTo("degree-plan.txt");
        UUID factId = UUID.fromString(fact.get("id").asString());

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(factId));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("PROGRAM_FACT_RECORDED");
        assertThat(entries.get(0).detail().get("quote")).isEqualTo("requires a total of 60 semester credit hours");

        var replaced = fx.fact(t, programId, "TOTAL_CREDITS", "61", plan, 1, "a total of 60 semester credit hours");
        assertThat(replaced).hasStatus(201);
        var list = fx.read(mvc.get().uri("/api/v1/programs/" + programId + "/facts").with(user(t.reviewer())).exchange());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("value").asInt()).isEqualTo(61);
        assertThat(list.get(0).get("current").asBoolean()).isTrue();
        assertThat(list.get(1).get("current").asBoolean()).isFalse();
    }

    @Test
    void fabricatedQuoteAndForeignDocumentAreRejected() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        var bad = fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 48 semester credit hours");
        assertThat(bad).hasStatus(400);
        assertThat(fx.read(bad).get("detail").asString()).contains("quote was not found");
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 7, "a total of 60 semester credit hours")).hasStatus(400);

        UUID otherProgram = fx.program(t, List.of("TX"));
        var foreign = fx.fact(t, otherProgram, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours");
        assertThat(foreign).hasStatus(400);
        assertThat(fx.read(foreign).get("detail").asString()).contains("different program");
    }

    @Test
    void onlyContributorsAndAdminsRecordFacts() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        var result = mvc.post().uri("/api/v1/programs/" + programId + "/facts").with(user(t.reviewer())).with(com.lexvay.common.TestCsrf.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("key", "TOTAL_CREDITS", "value", "60", "documentId", plan.toString(),
                        "pageNumber", 1, "quote", "a total of 60 semester credit hours")))
                .exchange();
        assertThat(result).hasStatus(403);
    }
}
