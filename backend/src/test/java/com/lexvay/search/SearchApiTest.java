package com.lexvay.search;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentKind;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.ExtractedPage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

class SearchApiTest extends AbstractSearchTest {

    @Autowired DocumentRepository documents;

    private static String joined(JsonNode hit, boolean matchesOnly) {
        StringBuilder text = new StringBuilder();
        for (JsonNode s : hit.get("segments")) {
            if (!matchesOnly || s.get("match").asBoolean()) {
                text.append(s.get("text").asString());
            }
        }
        return text.toString();
    }

    private List<String> pageTexts(JsonNode hits) {
        List<String> texts = new ArrayList<>();
        for (JsonNode hit : hits) {
            UUID documentId = UUID.fromString(hit.get("documentId").asString());
            texts.add(TenantContext.runAs(tenant.tenantId(),
                    () -> documents.page(documentId, hit.get("pageNumber").asInt()).orElseThrow().text()));
        }
        return texts;
    }

    private int auditEntries() throws Exception {
        return json.readTree(mvc.get().uri("/api/v1/audit?limit=500").with(user(tenant.admin())).exchange()
                .getResponse().getContentAsString()).size();
    }

    @Test
    void phraseQueryFindsThePageAndMarksTheMatchedWordsAsSegments() throws Exception {
        JsonNode hits = search(programId, "\"duty to warn\"", "");
        assertThat(hits).isNotEmpty();
        JsonNode top = hits.get(0);
        assertThat(top.get("filename").asString()).isEqualTo("coun605-syllabus.pdf");
        assertThat(top.get("kind").asString()).isEqualTo("SYLLABUS");
        assertThat(top.get("courseCode").asString()).isEqualTo("COUN 605");
        assertThat(top.get("courseRequired").asBoolean()).isTrue();
        assertThat(top.get("pageNumber").asInt()).isEqualTo(1);
        assertThat(top.get("rank").asDouble()).isPositive();
        assertThat(top.get("documentId").asString()).isNotBlank();
        assertThat(joined(top, true).toLowerCase()).contains("duty").contains("warn");
        assertThat(pageTexts(hits)).allSatisfy(t -> assertThat(t.toLowerCase()).contains("duty to warn"));
    }

    @Test
    void negationExcludesPagesThatContainTheTerm() throws Exception {
        JsonNode plain = search(programId, "ethics", "");
        JsonNode negated = search(programId, "ethics -group", "");
        assertThat(pageTexts(plain)).anyMatch(t -> t.toLowerCase().contains("group"));
        assertThat(negated).isNotEmpty().hasSizeLessThan(plain.size());
        assertThat(pageTexts(negated)).allSatisfy(t -> assertThat(t.toLowerCase()).containsPattern("ethic").doesNotContainPattern("\\bgroup"));
    }

    @Test
    void orQueryMatchesEitherTerm() throws Exception {
        JsonNode either = search(programId, "\"duty to warn\" OR practicum", "");
        JsonNode warn = search(programId, "\"duty to warn\"", "");
        JsonNode practicum = search(programId, "practicum", "");
        assertThat(either.size()).isGreaterThanOrEqualTo(Math.max(warn.size(), practicum.size()));
    }

    @Test
    void hitsAreOrderedByRankHighestFirst() throws Exception {
        JsonNode hits = search(programId, "counseling", "");
        assertThat(hits.size()).isGreaterThan(3);
        double previous = Double.MAX_VALUE;
        for (JsonNode hit : hits) {
            double rank = hit.get("rank").asDouble();
            assertThat(rank).isLessThanOrEqualTo(previous);
            previous = rank;
        }
        assertThat(hits.get(0).get("rank").asDouble()).isGreaterThan(hits.get(hits.size() - 1).get("rank").asDouble());
    }

    @Test
    void segmentsAreTextOnlyWithNoDelimitersOrEmptyRuns() throws Exception {
        for (JsonNode hit : search(programId, "counseling ethics practicum", "&limit=50")) {
            assertThat(hit.get("segments")).isNotEmpty();
            for (JsonNode s : hit.get("segments")) {
                assertThat(s.get("text").asString()).isNotEmpty().doesNotContain("\u0001", "\u0002");
                assertThat(s.get("match").isBoolean()).isTrue();
            }
            assertThat(hit.has("headline")).isFalse();
        }
    }

    @Test
    void kindFilterNarrowsToThatKindOfDocument() throws Exception {
        JsonNode all = search(programId, "counseling", "&limit=50");
        JsonNode handbook = search(programId, "counseling", "&limit=50&kind=HANDBOOK");
        assertThat(handbook).isNotEmpty().hasSizeLessThan(all.size());
        for (JsonNode hit : handbook) {
            assertThat(hit.get("kind").asString()).isEqualTo("HANDBOOK");
            assertThat(hit.get("courseCode").isNull()).isTrue();
        }
    }

    @Test
    void courseVersionFilterNarrowsToThatCoursesDocuments() throws Exception {
        UUID coun605 = courseVersionByCode.get("COUN 605");
        JsonNode hits = search(programId, "ethics", "&courseVersionId=" + coun605);
        assertThat(hits).isNotEmpty();
        for (JsonNode hit : hits) {
            assertThat(hit.get("courseCode").asString()).isEqualTo("COUN 605");
        }
        assertThat(search(programId, "ethics", "&courseVersionId=" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void ruleTextIsNeverSearchedAndCannotBeAskedFor() throws Exception {
        UUID ruleDocument = UUID.randomUUID();
        TenantContext.runAs(tenant.tenantId(), () -> {
            documents.insert(ruleDocument, tenant.tenantId(), null, null, "rule.txt", "a".repeat(64), "rules/rule.txt",
                    DocumentKind.RULE_TEXT, tenant.admin().id());
            documents.insertPage(tenant.tenantId(), ruleDocument, ExtractedPage.textLayer(1, "Zymurgy certification of fermentation", 0, 37),
                    tenant.admin().id());
        });
        assertThat(search(programId, "zymurgy", "")).isEmpty();
        assertThat(searchAs(tenant.reviewer(), searchUri(programId, "ethics", "&kind=RULE_TEXT"))).hasStatus(400);
    }

    @Test
    void limitDefaultsToTwentyAndIsCappedAtFifty() throws Exception {
        UUID manyPages = UUID.randomUUID();
        TenantContext.runAs(tenant.tenantId(), () -> {
            documents.insert(manyPages, tenant.tenantId(), programId, null, "quokka.txt", "b".repeat(64), "documents/quokka.txt",
                    DocumentKind.HANDBOOK, tenant.admin().id());
            for (int page = 1; page <= 60; page++) {
                documents.insertPage(tenant.tenantId(), manyPages, ExtractedPage.textLayer(page, "Quokka habitat notes, page " + page, 0, 30),
                        tenant.admin().id());
            }
        });
        assertThat(search(programId, "quokka", "")).hasSize(20);
        assertThat(search(programId, "quokka", "&limit=3")).hasSize(3);
        assertThat(search(programId, "quokka", "&limit=50")).hasSize(50);
        for (String bad : List.of("&limit=51", "&limit=0", "&limit=-1", "&limit=abc")) {
            assertThat(searchAs(tenant.reviewer(), searchUri(programId, "counseling", bad))).as(bad).hasStatus(400);
        }
    }

    @Test
    void blankMissingOrOverlongQueryIs400() {
        for (String query : List.of("", "   ", "x".repeat(501))) {
            assertThat(searchAs(tenant.reviewer(), searchUri(programId, query, ""))).as("q of %d chars", query.length()).hasStatus(400);
        }
        assertThat(searchAs(tenant.reviewer(), URI.create("/api/v1/programs/" + programId + "/search"))).hasStatus(400);
    }

    @Test
    void queryWithSyntaxCharactersIsHarmlessText() throws Exception {
        for (String hostile : List.of("'; DROP TABLE document_page; --", "(((", "a & | ! b", "\"unclosed", ":* <-> \\", "-", "the")) {
            assertThat(search(programId, hostile, "")).as(hostile).isNotNull();
        }
        assertThat(search(programId, "ethics", "")).isNotEmpty();
    }

    @Test
    void anotherTenantGets404AndSeesNothing() {
        var other = tenants.create("outsider");
        assertThat(mvc.get().uri("/api/v1/programs/{id}/search?q=ethics", programId).with(user(other.admin())).exchange())
                .hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/programs/{id}/search?q=ethics", UUID.randomUUID()).with(user(tenant.admin())).exchange())
                .hasStatus(404);
    }

    @Test
    void unauthenticatedIs401() {
        assertThat(mvc.get().uri("/api/v1/programs/{id}/search?q=ethics", programId).exchange()).hasStatus(401);
    }

    @Test
    void everyRoleMaySearch() {
        for (var u : List.of(tenant.admin(), tenant.contributor(), tenant.reviewer(), tenant.signer())) {
            assertThat(mvc.get().uri("/api/v1/programs/{id}/search?q=ethics", programId).with(user(u)).exchange()).hasStatus(200);
        }
    }

    @Test
    void searchingWritesNothingNotEvenAnAuditRow() throws Exception {
        int before = auditEntries();
        search(programId, "ethics", "");
        assertThat(before).isPositive();
        assertThat(auditEntries()).isEqualTo(before);
        assertThat(mvc.post().uri("/api/v1/programs/{id}/search?q=ethics", programId).with(user(tenant.reviewer())).with(csrf())
                .exchange().getResponse().getStatus()).isIn(404, 405);
    }
}
