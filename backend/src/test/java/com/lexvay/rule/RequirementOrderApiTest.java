package com.lexvay.rule;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** Requirements keep the order of their list (FIX-1): twelve refs would sort "CO-10" before "CO-2" as text. */
class RequirementOrderApiTest extends AbstractAssessmentTest {

    private static final List<String> CO_1_TO_12 = IntStream.rangeClosed(1, 12).mapToObj(n -> "CO-" + n).toList();

    private String twelve() {
        return fx.requirements("CO", IntStream.rangeClosed(1, 12)
                .mapToObj(n -> AssessmentFixtures.subject("Subject " + n, "coursework in subject number " + n)).toList());
    }

    private static List<String> refs(JsonNode requirements) {
        List<String> refs = new ArrayList<>();
        requirements.forEach(r -> refs.add(r.get("ref").asString()));
        return refs;
    }

    private JsonNode listed(UUID ruleVersionId) {
        var result = mvc.get().uri("/api/v1/rule-versions/" + ruleVersionId + "/requirements").with(user(t.reviewer())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result).get("requirements");
    }

    @Test
    void requirementsEndpointListsTwelveRequirementsInListOrder() {
        UUID id = fx.approvedRuleVersion(t, "CO", "Demo rule set Colorado twelve", "Fictional.", twelve());

        JsonNode requirements = listed(id);

        assertThat(refs(requirements)).containsExactlyElementsOf(CO_1_TO_12);
        assertThat(requirements).extracting(r -> r.get("ordinal").asInt()).containsExactlyElementsOf(IntStream.rangeClosed(1, 12).boxed().toList());
    }

    @Test
    void mappingsEndpointListsTwelveRequirementsInListOrder() {
        UUID programId = fx.program(t, List.of("CO"));
        fx.approvedRuleVersion(t, "CO", "Demo rule set Colorado mappings", "Fictional.", twelve());

        UUID assessmentId = fx.runAssessment(t, programId);

        List<String> mappingRefs = new ArrayList<>();
        fx.mappings(t, assessmentId, "CO").forEach(m -> mappingRefs.add(m.get("requirement").get("ref").asString()));
        assertThat(mappingRefs).containsExactlyElementsOf(CO_1_TO_12);
    }

    @Test
    void diffListsTwelveRequirementsInTheNewerVersionsOrder() {
        UUID older = fx.approvedRuleVersion(t, "CO", "Demo rule set Colorado older", "Fictional older.", twelve());
        UUID newer = fx.approvedRuleVersion(t, "CO", "Demo rule set Colorado newer", "Fictional newer.", twelve());

        var result = mvc.get().uri("/api/v1/rule-versions/" + newer + "/diff?against=" + older).with(user(t.reviewer())).exchange();

        assertThat(result).hasStatus(200);
        JsonNode rows = fx.read(result).get("requirements");
        assertThat(rows).extracting(r -> r.get("status").asString()).containsOnly("UNCHANGED");
        assertThat(rows).extracting(r -> r.get("after").get("ref").asString()).containsExactlyElementsOf(CO_1_TO_12);
    }

    @Test
    void replaceKeepsTheSubmittedOrderAndNumbersItFromOne() {
        UUID id = fx.ruleVersion(t, "CO", "Demo rule set Colorado replace", "Fictional.", twelve());
        // Neither numeric nor text order: the reviewer's order is the order.
        String replacement = """
                {"requirements":[
                  {"ref":"CO-3","title":"Third","text":"coursework in the third subject","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null},
                  {"ref":"CO-1","title":"First","text":"coursework in the first subject","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null},
                  {"ref":"CO-10","title":"Tenth","text":"coursework in the tenth subject","type":"SUBJECT",
                   "numeric_field":null,"numeric_operator":null,"numeric_value":null}
                ]}""";

        var replaced = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replacement).exchange();

        assertThat(replaced).hasStatus(200);
        JsonNode returned = fx.read(replaced).get("requirements");
        assertThat(refs(returned)).containsExactly("CO-3", "CO-1", "CO-10");
        assertThat(returned).extracting(r -> r.get("ordinal").asInt()).containsExactly(1, 2, 3);
        assertThat(refs(listed(id))).containsExactly("CO-3", "CO-1", "CO-10");

        assertThat(mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange())
                .hasStatus(200);
        assertThat(refs(listed(id))).as("approval does not reorder").containsExactly("CO-3", "CO-1", "CO-10");
    }
}
