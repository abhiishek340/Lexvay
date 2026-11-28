package com.lexvay.rule;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.common.TestTenants;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** The diff API on rule versions built through the real API: renumbering, additions, removals, unapproved lists. */
class RuleVersionDiffApiTest extends AbstractAssessmentTest {

    private static final String ETHICS = "coursework in professional ethics and legal standards";
    private static final String CREDITS_60 = "a graduate degree of at least 60 semester credit hours";
    private static final String CREDITS_66 = "a graduate degree of at least 66 semester credit hours";

    /** Creates a rule version and runs its (scripted) extraction. Not approved. */
    private UUID version(TestTenants.TenantWithUsers tenant, String jurisdiction, String licenseType, String label, UUID supersedes,
                         String ruleText, String requirementsJson) {
        fake.script("EXTRACT:" + label, requirementsJson);
        Map<String, Object> request = new HashMap<>(Map.of("jurisdiction", jurisdiction, "licenseType", licenseType, "label", label,
                "sourceUrl", "https://example.invalid/" + label.replace(' ', '-'), "effectiveFrom", LocalDate.of(2027, 1, 1).toString(), "legalStatus", "IN_FORCE", "datesBasis", "Fictional test data",
                "text", ruleText));
        if (supersedes != null) {
            request.put("supersedesId", supersedes.toString());
        }
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(tenant.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)).exchange();
        assertThat(created).hasStatus(202);
        runner.runPending();
        return UUID.fromString(fx.read(created).get("ruleVersionId").asString());
    }

    private JsonNode diff(TestTenants.TenantWithUsers tenant, UUID id, String query) {
        var result = mvc.get().uri("/api/v1/rule-versions/" + id + "/diff" + query).with(user(tenant.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    private static JsonNode row(JsonNode diff, String status) {
        for (JsonNode r : diff.get("requirements")) {
            if (r.get("status").asString().equals(status)) {
                return r;
            }
        }
        throw new AssertionError("No " + status + " row");
    }

    @Test
    void renumberedEditedAddedAndRemovedRequirementsOnAnUnapprovedList() {
        String first = fx.requirements("CA", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, CREDITS_60),
                AssessmentFixtures.specific("Telehealth", "training in telehealth practice"),
                AssessmentFixtures.subject("Ethics", ETHICS)));
        UUID v1 = version(t, "CA", AssessmentFixtures.LICENSE, "California rev 1", null, "Line one\nAt least 60 hours.\n", first);
        // Renumbered CA-n to CAL-n: ethics is unchanged apart from its ref and capitalization; credits go 60 to 66
        // (8 of 10 distinct words in common, exactly the threshold); telehealth is gone; supervision is new.
        String second = fx.requirements("CAL", List.of(
                AssessmentFixtures.subject("Ethics", "Coursework in professional ethics and legal standards."),
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 66, CREDITS_66),
                AssessmentFixtures.subject("Supervision", "documented clinical supervision by a licensed supervisor")));
        UUID v2 = version(t, "CA", AssessmentFixtures.LICENSE, "California rev 2", v1, "Line one\nAt least 66 hours.\nNew line\n", second);

        JsonNode diff = diff(t, v2, "");

        assertThat(diff.get("summary").get("added").asInt()).isEqualTo(1);
        assertThat(diff.get("summary").get("removed").asInt()).isEqualTo(1);
        assertThat(diff.get("summary").get("changed").asInt()).isEqualTo(2);
        assertThat(diff.get("summary").get("unchanged").asInt()).isZero();
        List<String> order = new java.util.ArrayList<>();
        diff.get("requirements").forEach(r -> order.add(r.get("status").asString() + ":"
                + (r.get("after").isNull() ? r.get("before") : r.get("after")).get("ref").asString()));
        assertThat(order).as("newer version's order, removed last").containsExactly("CHANGED:CAL-1", "CHANGED:CAL-2", "ADDED:CAL-3", "REMOVED:CA-2");

        JsonNode ethics = diff.get("requirements").get(0);
        assertThat(ethics.get("refChanged").asBoolean()).isTrue();
        assertThat(ethics.get("before").get("ref").asString()).isEqualTo("CA-3");
        assertThat(ethics.get("changes")).as("same text up to capitalization: only the text differs").extracting(JsonNode::asString).containsExactly("TEXT");

        JsonNode credits = diff.get("requirements").get(1);
        assertThat(credits.get("refChanged").asBoolean()).isTrue();
        assertThat(credits.get("before").get("ref").asString()).isEqualTo("CA-1");
        assertThat(credits.get("changes")).extracting(JsonNode::asString).containsExactly("TEXT", "NUMERIC");
        assertThat(credits.get("textDiff")).isNotEmpty();

        JsonNode added = row(diff, "ADDED");
        assertThat(added.get("before").isNull()).isTrue();
        assertThat(added.get("textDiff").isNull()).isTrue();
        assertThat(added.get("changes")).isEmpty();
        JsonNode removed = row(diff, "REMOVED");
        assertThat(removed.get("after").isNull()).isTrue();
        assertThat(removed.get("before").get("title").asString()).isEqualTo("Telehealth");

        assertThat(diff.get("ruleTextDiff")).anySatisfy(s -> assertThat(s.get("op").asString()).isEqualTo("INSERT"));
    }

    @Test
    void aTitleOnlyChangeIsChangedWithTheTitleAspect() {
        String first = fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", ETHICS)));
        UUID v1 = version(t, "CA", AssessmentFixtures.LICENSE, "California title 1", null, "Title rule one", first);
        String second = fx.requirements("CA", List.of(AssessmentFixtures.subject("Professional ethics", ETHICS)));
        UUID v2 = version(t, "CA", AssessmentFixtures.LICENSE, "California title 2", v1, "Title rule two", second);

        JsonNode changed = row(diff(t, v2, "?against=" + v1), "CHANGED");
        assertThat(changed.get("refChanged").asBoolean()).isFalse();
        assertThat(changed.get("changes")).extracting(JsonNode::asString).containsExactly("TITLE");
    }

    @Test
    void aVersionWithoutASupersededOneAndWithoutAgainstIsA400() {
        UUID only = version(t, "CA", AssessmentFixtures.LICENSE, "California alone", null, "Alone",
                fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", ETHICS))));
        var result = mvc.get().uri("/api/v1/rule-versions/" + only + "/diff").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(400);
        assertThat(fx.read(result).get("detail").asString()).contains("does not supersede");
    }

    @Test
    void differentJurisdictionsOrLicenseTypesAreA400() {
        String reqs = fx.requirements("X", List.of(AssessmentFixtures.subject("Ethics", ETHICS)));
        UUID california = version(t, "CA", AssessmentFixtures.LICENSE, "California base", null, "California base text", reqs);
        UUID texas = version(t, "TX", AssessmentFixtures.LICENSE, "Texas base", null, "Texas base text", reqs);
        UUID otherLicense = version(t, "CA", "SCHOOL_COUNSELING", "California school", null, "California school text", reqs);

        var jurisdiction = mvc.get().uri("/api/v1/rule-versions/" + california + "/diff?against=" + texas).with(user(t.reviewer())).exchange();
        assertThat(jurisdiction).hasStatus(400);
        assertThat(fx.read(jurisdiction).get("detail").asString()).contains("same jurisdiction and license type");
        var license = mvc.get().uri("/api/v1/rule-versions/" + california + "/diff?against=" + otherLicense).with(user(t.reviewer())).exchange();
        assertThat(license).hasStatus(400);
        assertThat(fx.read(license).get("detail").asString()).contains("same jurisdiction and license type");
    }

    @Test
    void aMalformedAgainstIsA400() {
        var result = mvc.get().uri("/api/v1/rule-versions/" + UUID.randomUUID() + "/diff?against=not-a-uuid").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(400);
    }

    @Test
    void anotherTenantsVersionIsA404EvenAsTheAgainstParameter() {
        String reqs = fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", ETHICS)));
        UUID mine = version(t, "CA", AssessmentFixtures.LICENSE, "California mine", null, "Mine text", reqs);
        TestTenants.TenantWithUsers other = tenants.create("diff-other");
        UUID theirs = version(other, "CA", AssessmentFixtures.LICENSE, "California theirs", null, "Theirs text", reqs);

        assertThat(mvc.get().uri("/api/v1/rule-versions/" + mine + "/diff?against=" + theirs).with(user(t.reviewer())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + theirs + "/diff?against=" + mine).with(user(t.reviewer())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + theirs + "/diff?against=" + mine).with(user(other.reviewer())).exchange()).hasStatus(404);
    }

    @Test
    void unauthenticatedIsA401() {
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + UUID.randomUUID() + "/diff").exchange()).hasStatus(401);
    }
}
