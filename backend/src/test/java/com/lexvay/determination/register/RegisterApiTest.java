package com.lexvay.determination.register;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.audit.AuditFilter;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.determination.VersionsHash;
import com.lexvay.review.ReviewFixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/**
 * The register, the three lists and the CSV export on a fixture with every case: a program targeting AZ, CA, CO, TX
 * and UT where CA is signed "determined to meet", UT "determined not to meet", AZ "no determination made", CO was
 * signed and then reopened by a new rule version, and TX was never signed. A second program signs CA as well.
 */
class RegisterApiTest extends AbstractAssessmentTest {

    @Autowired AuditRepository audit;

    ReviewFixtures rf;
    UUID program;
    UUID otherProgram;
    UUID courseVersion;
    UUID assessment;
    final Map<String, UUID> ruleVersions = new LinkedHashMap<>();
    final Map<String, UUID> signed = new LinkedHashMap<>();
    UUID otherSigned;
    UUID coloradoRevision;

    @BeforeEach
    void fixture() throws Exception {
        rf = new ReviewFixtures(mvc, json, fx);
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        program = fx.program(t, List.of("UT", "CA", "AZ", "CO", "TX"));
        courseVersion = fx.course(t, program, "COUN 605", true);
        credits(program);
        for (String code : List.of("AZ", "CA", "CO", "TX", "UT")) {
            ruleVersions.put(code, fx.approvedRuleVersion(t, code, "Demo rule set " + code + " " + suffix, "Fictional " + code + " rule.",
                    fx.requirements(code, List.of(AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "at least 60 semester hours")))));
        }
        assessment = fx.runAssessment(t, program);
        signed.put("CA", acceptAndSign(program, assessment, "CA", "MEETS"));
        signed.put("UT", acceptAndSign(program, assessment, "UT", "DOES_NOT_MEET"));
        signed.put("AZ", acceptAndSign(program, assessment, "AZ", "NOT_DETERMINED"));
        signed.put("CO", acceptAndSign(program, assessment, "CO", "MEETS"));
        coloradoRevision = reviseColorado(suffix);

        otherProgram = fx.program(t, List.of("CA"));
        fx.course(t, otherProgram, "COUN 700", true);
        credits(otherProgram);
        otherSigned = acceptAndSign(otherProgram, fx.runAssessment(t, otherProgram), "CA", "DOES_NOT_MEET");
    }

    private void credits(UUID programId) {
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
    }

    private UUID acceptAndSign(UUID programId, UUID assessmentId, String code, String outcome) {
        for (JsonNode mapping : fx.mappings(t, assessmentId, code)) {
            assertThat(rf.review(t.reviewer(), UUID.fromString(mapping.get("id").asString()), "ACCEPTED", null)).hasStatus(201);
        }
        MvcTestResult result = rf.sign(t.signer(), programId, code, outcome);
        assertThat(result).hasStatus(201);
        return UUID.fromString(fx.read(result).get("id").asString());
    }

    /** A superseding Colorado rule version reopens the Colorado determination (ChangeService). */
    private UUID reviseColorado(String suffix) {
        String label = "Demo rule set CO " + suffix + " revision 2";
        fake.script("EXTRACT:" + label, fx.requirements("CO", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 66, "at least 66 semester hours"))));
        Map<String, Object> body = new HashMap<>(Map.of("jurisdiction", "CO", "licenseType", AssessmentFixtures.LICENSE, "label", label,
                "sourceUrl", "https://example.invalid/CO-r2", "effectiveFrom", LocalDate.of(2027, 1, 1).toString(),
                "text", "Fictional revised CO rule.", "supersedesId", ruleVersions.get("CO").toString()));
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
        assertThat(created).hasStatus(202);
        runner.runPending();
        return UUID.fromString(fx.read(created).get("ruleVersionId").asString());
    }

    private JsonNode get(String uri) {
        MvcTestResult result = mvc.get().uri(uri).with(user(t.contributor())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private static List<String> ids(JsonNode rows) {
        List<String> ids = new ArrayList<>();
        rows.forEach(r -> ids.add(r.get("determinationId").asString()));
        return ids;
    }

    private static List<String> codes(JsonNode entries) {
        List<String> codes = new ArrayList<>();
        entries.forEach(e -> codes.add(e.get("jurisdictionCode").asString()));
        return codes;
    }

    @Test
    void theRegisterListsEveryCurrentSignatureAsSignedInProgramAndJurisdictionOrder() {
        JsonNode rows = get("/api/v1/determinations/register");
        assertThat(rows).as("CO was reopened, TX never signed").hasSize(4);
        // Both programs share a name; within it, the jurisdiction code orders the rows of each program.
        List<String> mine = new ArrayList<>();
        rows.forEach(r -> {
            if (r.get("programId").asString().equals(program.toString())) {
                mine.add(r.get("jurisdictionCode").asString());
            }
        });
        assertThat(mine).containsExactly("AZ", "CA", "UT");
        assertThat(ids(rows)).containsExactlyInAnyOrder(signed.get("CA").toString(), signed.get("UT").toString(),
                signed.get("AZ").toString(), otherSigned.toString());

        JsonNode california = rows.get(ids(rows).indexOf(signed.get("CA").toString()));
        assertThat(california.get("programName").asString()).isEqualTo("M.S. Clinical Mental Health Counseling");
        assertThat(california.get("licenseType").asString()).isEqualTo(AssessmentFixtures.LICENSE);
        assertThat(california.get("catalogYear").asString()).isEqualTo("2026-27");
        assertThat(california.get("jurisdictionName").asString()).isEqualTo("California");
        assertThat(california.get("outcome").asString()).isEqualTo("MEETS");
        assertThat(california.get("status").asString()).isEqualTo("SIGNED");
        assertThat(california.get("signedBy").asString()).isEqualTo(t.signer().id().toString());
        assertThat(california.get("signedByName").asString()).isEqualTo(t.signer().displayName());
        assertThat(california.get("signedAt").isNull()).isFalse();
        assertThat(california.get("ruleVersionId").asString()).isEqualTo(ruleVersions.get("CA").toString());
        assertThat(california.get("ruleVersionLabel").asString()).startsWith("Demo rule set CA");
        assertThat(california.get("ruleEffectiveFrom").asString()).isEqualTo("2026-01-01");
        assertThat(california.get("versionsHash").asString()).isEqualTo(VersionsHash.compute(ruleVersions.get("CA"), List.of(courseVersion)));
        assertThat(california.get("assessmentId").isNull()).isFalse();
        assertThat(california.get("courseVersionCount").asInt()).isEqualTo(1);
    }

    @Test
    void filtersNarrowTheRegisterAndIncludeReopenedAddsTheReopenedSignature() {
        String base = "/api/v1/determinations/register";
        assertThat(ids(get(base + "?programId=" + program))).containsExactly(
                signed.get("AZ").toString(), signed.get("CA").toString(), signed.get("UT").toString());
        assertThat(ids(get(base + "?jurisdiction=CA"))).containsExactlyInAnyOrder(signed.get("CA").toString(), otherSigned.toString());
        assertThat(ids(get(base + "?outcome=DOES_NOT_MEET"))).containsExactlyInAnyOrder(signed.get("UT").toString(), otherSigned.toString());
        assertThat(ids(get(base + "?programId=" + program + "&jurisdiction=CA&outcome=MEETS"))).containsExactly(signed.get("CA").toString());
        assertThat(get(base + "?jurisdiction=TX")).isEmpty();
        assertThat(get(base + "?programId=" + UUID.randomUUID())).isEmpty();

        JsonNode withReopened = get(base + "?programId=" + program + "&current=false");
        assertThat(ids(withReopened)).containsExactly(signed.get("AZ").toString(), signed.get("CA").toString(),
                signed.get("CO").toString(), signed.get("UT").toString());
        JsonNode colorado = withReopened.get(2);
        assertThat(colorado.get("status").asString()).isEqualTo("REOPENED");
        assertThat(colorado.get("outcome").asString()).as("exactly as signed").isEqualTo("MEETS");
        assertThat(colorado.get("ruleVersionId").asString()).as("the version it was signed on").isEqualTo(ruleVersions.get("CO").toString());
    }

    @Test
    void theThreeListsPartitionTheTargetsExactly() {
        JsonNode lists = get("/api/v1/programs/" + program + "/determination-lists");
        assertThat(lists.get("program").get("id").asString()).isEqualTo(program.toString());
        assertThat(lists.get("program").get("name").asString()).isEqualTo("M.S. Clinical Mental Health Counseling");
        assertThat(lists.get("generatedAt").isNull()).isFalse();

        assertThat(codes(lists.get("determinedToMeet"))).containsExactly("CA");
        assertThat(codes(lists.get("determinedNotToMeet"))).containsExactly("UT");
        assertThat(codes(lists.get("noDeterminationMade"))).containsExactly("AZ", "CO", "TX");
        List<String> all = new ArrayList<>();
        for (String list : List.of("determinedToMeet", "determinedNotToMeet", "noDeterminationMade")) {
            all.addAll(codes(lists.get(list)));
        }
        assertThat(all).as("every target exactly once, nothing else").containsExactlyInAnyOrder("AZ", "CA", "CO", "TX", "UT");

        JsonNode california = lists.get("determinedToMeet").get(0);
        assertThat(california.get("jurisdictionName").asString()).isEqualTo("California");
        assertThat(california.get("determinationId").asString()).isEqualTo(signed.get("CA").toString());
        assertThat(california.get("signedByName").asString()).isEqualTo(t.signer().displayName());
        assertThat(california.get("ruleVersionLabel").asString()).startsWith("Demo rule set CA");
        assertThat(california.get("versionsHash").asString()).hasSize(64);
        assertThat(california.get("reason").isNull()).isTrue();

        JsonNode arizona = lists.get("noDeterminationMade").get(0);
        assertThat(arizona.get("reason").asString()).isEqualTo("SIGNED_NO_DETERMINATION");
        assertThat(arizona.get("determinationId").asString()).isEqualTo(signed.get("AZ").toString());
        assertThat(arizona.get("signedAt").isNull()).isFalse();
        assertThat(arizona.get("reopenedDeterminationId").isNull()).isTrue();

        JsonNode colorado = lists.get("noDeterminationMade").get(1);
        assertThat(colorado.get("reason").asString()).isEqualTo("NO_CURRENT_SIGNATURE");
        assertThat(colorado.get("determinationId").isNull()).isTrue();
        assertThat(colorado.get("signedByName").isNull()).isTrue();
        assertThat(colorado.get("reopenedDeterminationId").asString()).isEqualTo(signed.get("CO").toString());
        var reopenedAudit = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(signed.get("CO"))).stream()
                .filter(e -> e.action().equals("DETERMINATION_REOPENED")).findFirst().orElseThrow();
        assertThat(OffsetDateTime.parse(colorado.get("reopenedAt").asString())).isAtSameInstantAs(reopenedAudit.createdAt());

        JsonNode texas = lists.get("noDeterminationMade").get(2);
        assertThat(texas.get("reason").asString()).isEqualTo("NO_CURRENT_SIGNATURE");
        assertThat(texas.get("reopenedDeterminationId").isNull()).isTrue();
        assertThat(texas.get("reopenedAt").isNull()).isTrue();

        JsonNode other = get("/api/v1/programs/" + otherProgram + "/determination-lists");
        assertThat(codes(other.get("determinedToMeet"))).isEmpty();
        assertThat(codes(other.get("determinedNotToMeet"))).as("the other program's own signature only").containsExactly("CA");
        assertThat(codes(other.get("noDeterminationMade"))).isEmpty();
    }

    @Test
    void signingAgainAfterTheReopeningMovesTheTargetToTheListItWasSignedInto() {
        runner.runPending();
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + coloradoRevision + "/requirements/approve").with(user(t.reviewer())).with(csrf())
                .exchange()).hasStatus(200);
        UUID event = UUID.fromString(get("/api/v1/change-events").get(0).get("id").asString());
        assertThat(mvc.post().uri("/api/v1/change-events/" + event + "/rerun").with(user(t.reviewer())).with(csrf()).exchange()).hasStatus(202);
        runner.runPending();
        UUID resigned = acceptAndSign(program, assessment, "CO", "DOES_NOT_MEET");

        JsonNode lists = get("/api/v1/programs/" + program + "/determination-lists");
        assertThat(codes(lists.get("determinedNotToMeet"))).containsExactly("CO", "UT");
        assertThat(lists.get("determinedNotToMeet").get(0).get("determinationId").asString()).isEqualTo(resigned.toString());
        assertThat(lists.get("determinedNotToMeet").get(0).get("reopenedDeterminationId").isNull()).isTrue();
        assertThat(codes(lists.get("noDeterminationMade"))).containsExactly("AZ", "TX");
    }

    @Test
    void theCsvExportIsRfc4180WithLabelsAndIsAudited() throws Exception {
        MvcTestResult result = mvc.get().uri("/api/v1/determinations/register.csv?programId=" + program + "&current=false")
                .with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"lexvay-determination-register-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"");
        String csv = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n", -1);
        assertThat(lines).as("the header and four records, each ending with CRLF").hasSize(6);
        assertThat(lines[5]).isEmpty();
        assertThat(lines[0]).isEqualTo("\"Determination ID\",\"Program ID\",\"Program\",\"License type\",\"Catalog year\",\"Jurisdiction code\","
                + "\"Jurisdiction\",\"Outcome\",\"Status\",\"Signed by (user ID)\",\"Signed by\",\"Signed at\",\"Rule version ID\","
                + "\"Rule version\",\"Rule effective from\",\"Versions hash\",\"Assessment ID\",\"Course versions\"");
        assertThat(lines[2]).startsWith("\"" + signed.get("CA") + "\",\"" + program + "\",\"M.S. Clinical Mental Health Counseling\",")
                .contains(",\"CA\",\"California\",\"Determined to meet\",\"Signed\",\"" + t.signer().id() + "\",\""
                        + t.signer().displayName() + "\",")
                .contains("\"2026-01-01\",\"" + VersionsHash.compute(ruleVersions.get("CA"), List.of(courseVersion)) + "\"")
                .endsWith(",\"1\"");
        assertThat(lines[3]).contains("\"Colorado\",\"Determined to meet\",\"Reopened\"");
        assertThat(lines[1]).contains("\"No determination made\"");
        assertThat(lines[4]).contains("\"Determined not to meet\"");

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.newestFirst(
                new AuditFilter("REGISTER_EXPORTED", null, null, null, null, null), 10, 0));
        assertThat(entries).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(t.reviewer().id());
            assertThat(e.entityType()).isEqualTo("determination_register");
            assertThat(e.detail()).containsEntry("programId", program.toString()).containsEntry("current", false)
                    .containsEntry("rowCount", 4).doesNotContainKeys("jurisdiction", "outcome");
        });
    }

    @Test
    void anotherTenantSeesNothing() {
        var other = tenants.create("register-other");
        var register = mvc.get().uri("/api/v1/determinations/register?current=false").with(user(other.admin())).exchange();
        assertThat(register).hasStatus(200);
        assertThat(fx.read(register)).isEmpty();
        assertThat(mvc.get().uri("/api/v1/programs/" + program + "/determination-lists").with(user(other.admin())).exchange()).hasStatus(404);
        var csv = mvc.get().uri("/api/v1/determinations/register.csv").with(user(other.admin())).exchange();
        assertThat(csv).hasStatus(200);
        assertThat(new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).split("\r\n")).hasSize(1);
    }
}
