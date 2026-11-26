package com.lexvay.determination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.review.ReviewFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DeterminationApiTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;

    ReviewFixtures rf;
    ReviewFixtures.Scenario s;

    @BeforeEach
    void scenario() {
        rf = new ReviewFixtures(mvc, json, fx);
        s = rf.californiaScenario(t, "sign " + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    void signingIsRefusedWith409UntilEveryMappingHasADecision() throws Exception {
        var incomplete = rf.sign(t.signer(), s.programId(), "CA", "MEETS");
        assertThat(incomplete).hasStatus(409);
        assertThat(fx.read(incomplete).get("detail").asString()).contains("3 of 3 requirements");

        rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", null);
        rf.review(t.reviewer(), s.missingMapping(), "GAP_CONFIRMED", "No telehealth content.");
        var stillOne = rf.sign(t.signer(), s.programId(), "CA", "MEETS");
        assertThat(stillOne).hasStatus(409);
        assertThat(fx.read(stillOne).get("detail").asString()).contains("1 of 3 requirements");

        var utah = rf.sign(t.signer(), s.programId(), "UT", "MEETS");
        assertThat(utah).as("UT has an undecided numeric mapping").hasStatus(409);
        var notTargeted = rf.sign(t.signer(), s.programId(), "TX", "MEETS");
        assertThat(notTargeted).hasStatus(400);
        assertThat(fx.read(notTargeted).get("detail").asString()).contains("not a target");
    }

    @Test
    void signerSignsWhenEveryMappingIsDecided() throws Exception {
        rf.decideAll(t, s);
        var signed = rf.sign(t.signer(), s.programId(), "CA", "DOES_NOT_MEET");
        assertThat(signed).hasStatus(201);
        var d = fx.read(signed);
        assertThat(d.get("outcome").asString()).isEqualTo("DOES_NOT_MEET");
        assertThat(d.get("status").asString()).isEqualTo("SIGNED");
        assertThat(d.get("jurisdiction").asString()).isEqualTo("CA");
        assertThat(d.get("signedBy").asString()).isEqualTo(t.signer().id().toString());
        assertThat(d.get("signedByName").asString()).isEqualTo(t.signer().displayName());
        assertThat(d.get("ruleVersionId").asString()).isEqualTo(s.ruleVersionId().toString());
        assertThat(d.get("ruleVersionLabel").asString()).startsWith("Demo rule set California");
        assertThat(d.get("assessmentId").asString()).isEqualTo(s.assessmentId().toString());
        List<String> courseVersions = new java.util.ArrayList<>();
        d.get("courseVersionIds").forEach(n -> courseVersions.add(n.asString()));
        assertThat(courseVersions).containsExactlyInAnyOrder(s.course605Version().toString(), s.course650Version().toString());
        String expectedHash = VersionsHash.compute(s.ruleVersionId(), List.of(s.course605Version(), s.course650Version()));
        assertThat(d.get("versionsHash").asString()).isEqualTo(expectedHash);
        UUID id = UUID.fromString(d.get("id").asString());

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(id));
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).action()).isEqualTo("DETERMINATION_SIGNED");
        assertThat(entries.get(0).userId()).isEqualTo(t.signer().id());
        assertThat(entries.get(0).detail().get("versionsHash")).isEqualTo(expectedHash);

        var list = fx.read(mvc.get().uri("/api/v1/programs/" + s.programId() + "/determinations").with(user(t.reviewer())).exchange());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asString()).isEqualTo(id.toString());

        assertThat(rf.sign(t.signer(), s.programId(), "CA", "MEETS")).as("already signed").hasStatus(409);
        var afterSigning = rf.review(t.reviewer(), s.foundMapping(), "ACCEPTED", "late change");
        assertThat(afterSigning).as("D-057").hasStatus(409);
        assertThat(fx.read(afterSigning).get("detail").asString()).contains("signed determination");

        var other = tenants.create("other");
        assertThat(mvc.get().uri("/api/v1/programs/" + s.programId() + "/determinations").with(user(other.admin())).exchange()).hasStatus(404);
    }

    @Test
    void noFinishedAssessmentIs409() throws Exception {
        UUID freshProgram = fx.program(t, List.of("CA"));
        var result = rf.sign(t.signer(), freshProgram, "CA", "NOT_DETERMINED");
        assertThat(result).hasStatus(409);
        assertThat(fx.read(result).get("detail").asString()).contains("No finished assessment");
    }
}
