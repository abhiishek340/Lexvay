package com.lexvay.seed;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Signs one jurisdiction of a seeded program through the API, for tests that need a signed determination on real seed data. */
public final class SeedSigning {

    public record Signed(String jurisdiction, UUID determinationId) {
    }

    private SeedSigning() {
    }

    /**
     * Decides every mapping of the first of {@code targets} whose mappings are all current (accepting what was found,
     * confirming the gap on the rest) and signs it "does not meet".
     */
    public static Signed signFirstCurrentJurisdiction(MockMvcTester mvc, ObjectMapper json, List<String> targets, UUID assessmentId,
                                                      UUID programId, LexvayUser reviewer, LexvayUser signer) throws Exception {
        for (String code : targets) {
            JsonNode mappings = body(json, mvc.get().uri("/api/v1/assessments/" + assessmentId + "/mappings?jurisdiction=" + code)
                    .with(user(reviewer)).exchange()).get("mappings");
            boolean current = true;
            for (JsonNode m : mappings) {
                current &= !m.get("stale").asBoolean();
            }
            if (!current) {
                continue;
            }
            for (JsonNode m : mappings) {
                boolean found = m.get("finding").asString().equals("EVIDENCE_FOUND");
                Map<String, Object> decision = found ? Map.of("kind", "ACCEPTED") : Map.of("kind", "GAP_CONFIRMED", "note", "Fixture decision.");
                body(json, mvc.post().uri("/api/v1/mappings/" + m.get("id").asString() + "/reviews").with(user(reviewer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(decision)).exchange());
            }
            JsonNode signed = body(json, mvc.post().uri("/api/v1/programs/" + programId + "/determinations").with(user(signer)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("jurisdiction", code, "outcome", "DOES_NOT_MEET"))).exchange());
            return new Signed(code, UUID.fromString(signed.get("id").asString()));
        }
        throw new AssertionError("no seeded jurisdiction has only current mappings");
    }

    private static JsonNode body(ObjectMapper json, MvcTestResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(content).isBetween(200, 299);
        return json.readTree(content);
    }
}
