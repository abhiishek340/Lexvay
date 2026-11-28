package com.lexvay.audit;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestCsv;
import com.lexvay.common.TestTenants;
import com.lexvay.tenant.DataMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** F5a: the audit log as a CSV file for an auditor (D-377). */
class AuditExportApiTest extends AbstractDatabaseTest {

    static final List<String> HEADER = List.of("Time (UTC)", "Actor", "Action", "Record type", "Record ID", "Details",
            "Seq", "Row hash", "Entry ID", "Actor ID");

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired AuditService audit;
    @Autowired AuditRepository repository;
    @Autowired AuditSealer sealer;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers t;
    UUID programId;

    @BeforeEach
    void setUp() throws Exception {
        t = tenants.create("auditexport");
        var created = mvc.post().uri("/api/v1/programs").with(user(t.contributor())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Export Program", "licenseType", "LPC", "catalogYear", "2026-27",
                        "jurisdictions", List.of("CA"))))
                .exchange();
        assertThat(created).hasStatus(201);
        programId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asString());
    }

    private MvcTestResult export(LexvayUser as, String query) {
        var request = mvc.get().uri(java.net.URI.create("/api/v1/audit.csv" + query)); // a URI, so %00 reaches the server as NUL
        return (as == null ? request : request.with(user(as))).exchange();
    }

    private List<List<String>> rows(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(200);
        List<List<String>> records = TestCsv.parse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(records.get(0)).as("a DEMO tenant's file says first that its data is fictional").containsExactly("Fictional demo data");
        assertThat(records.get(1)).isEqualTo(HEADER);
        return records.subList(2, records.size());
    }

    private List<AuditEntry> exportsOf() {
        return TenantContext.runAs(t.tenantId(), () -> repository.newestFirst(
                new AuditFilter("AUDIT_EXPORTED", null, null, null, null, null), 100, 0));
    }

    @Test
    void aTenantWithRealDataGetsTheFileWithoutTheDemoNotice() throws Exception {
        var live = tenants.create("auditexport-live", DataMode.LIVE);

        var result = export(live.admin(), "");

        assertThat(result).hasStatus(200);
        List<List<String>> records = TestCsv.parse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(records.get(0)).isEqualTo(HEADER);
    }

    @Test
    void onlyAdministratorsMayExport() {
        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer())) {
            assertThat(export(who, "")).hasStatus(403);
        }
        assertThat(export(null, "")).hasStatus(401);
        assertThat(exportsOf()).isEmpty();
    }

    @Test
    void theFileHasTheDocumentedColumnsInTheDocumentedFormat() throws Exception {
        TenantContext.runAs(t.tenantId(), () -> audit.recordAs(null, "SCHEDULED_CHECK", "rule_source", null, Map.of("note", "say \"hi\", then leave")));
        var result = export(t.admin(), "");

        assertThat(result.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"lexvay-audit-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"");
        assertThat(result.getResponse().getContentAsByteArray()[0]).as("no byte order mark").isEqualTo((byte) '"');

        List<List<String>> rows = rows(result);
        assertThat(rows).allSatisfy(row -> assertThat(row).hasSize(10));
        Instant previous = Instant.MAX;
        for (List<String> row : rows) {
            Instant time = Instant.parse(row.get(0));
            assertThat(time).as("newest first").isBeforeOrEqualTo(previous);
            previous = time;
            assertThat(row.get(5)).as("details are the JSON as text").satisfies(details -> assertThat(json.readTree(details).isObject()).isTrue());
        }

        List<String> system = rows.stream().filter(r -> r.get(2).equals("SCHEDULED_CHECK")).findFirst().orElseThrow();
        assertThat(system.get(1)).isEqualTo("System");
        assertThat(system.get(3)).isEqualTo("rule_source");
        assertThat(system.get(4)).as("no record").isEmpty();
        assertThat(json.readTree(system.get(5)).get("note").asString()).isEqualTo("say \"hi\", then leave");

        List<String> program = rows.stream().filter(r -> r.get(2).equals("PROGRAM_CREATED")).findFirst().orElseThrow();
        assertThat(program.get(1)).isEqualTo(t.contributor().displayName());
        assertThat(program.get(3)).isEqualTo("program");
        assertThat(program.get(4)).isEqualTo(programId.toString());
    }

    @Test
    void sealedEntriesCarryTheirSequenceAndHashAndPendingOnesDoNot() throws Exception {
        List<List<String>> before = rows(export(t.admin(), ""));
        assertThat(before).isNotEmpty().allSatisfy(row -> {
            assertThat(row.get(6)).as("pending: no seq").isEmpty();
            assertThat(row.get(7)).as("pending: no hash").isEmpty();
        });

        sealer.sealPending();

        List<List<String>> sealed = rows(export(t.admin(), ""));
        assertThat(sealed).allSatisfy(row -> {
            assertThat(row.get(6)).matches("[1-9][0-9]*");
            assertThat(row.get(7)).matches("[0-9a-f]{64}");
            assertThat(UUID.fromString(row.get(8))).isNotNull();
        });
        List<List<String>> bySeq = sealed.stream().sorted(java.util.Comparator.comparingLong(r -> Long.parseLong(r.get(6)))).toList();
        assertThat(bySeq.stream().map(r -> Long.parseLong(r.get(6))).toList())
                .as("sequence numbers are contiguous from 1").isEqualTo(java.util.stream.LongStream.rangeClosed(1, sealed.size()).boxed().toList());
        String previous = AuditCanonical.GENESIS_HASH;
        for (List<String> row : bySeq) {
            var entry = new AuditCanonical.Entry(UUID.fromString(row.get(8)), row.get(9).isEmpty() ? null : UUID.fromString(row.get(9)),
                    row.get(2), row.get(3), row.get(4).isEmpty() ? null : UUID.fromString(row.get(4)), row.get(5), Instant.parse(row.get(0)));
            assertThat(AuditCanonical.rowHash(t.tenantId(), Long.parseLong(row.get(6)), entry, previous))
                    .as("the file alone is enough to recompute the hash of seq %s", row.get(6)).isEqualTo(row.get(7));
            previous = row.get(7);
        }
        List<String> program = sealed.stream().filter(r -> r.get(2).equals("PROGRAM_CREATED")).findFirst().orElseThrow();
        assertThat(program.get(9)).isEqualTo(t.contributor().id().toString());
    }

    @Test
    void aNameThatASpreadsheetWouldEvaluateIsDefused() throws Exception {
        String name = "=HYPERLINK(\"http://evil.test\",\"x\")";
        var created = mvc.post().uri("/api/v1/users").with(user(t.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "formula-" + UUID.randomUUID() + "@example.test", "displayName", name, "role", "REVIEWER")))
                .exchange();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("user").get("id").asString());
        TenantContext.runAs(t.tenantId(), () -> audit.recordAs(id, "SOMETHING_DONE", "program", programId, Map.of()));

        List<String> row = rows(export(t.admin(), "?action=SOMETHING_DONE")).get(0);
        assertThat(row.get(1)).isEqualTo("'" + name);
    }

    @Test
    void theFiltersSelectExactlyWhatTheListSelects() throws Exception {
        TenantContext.runAs(t.tenantId(), () -> audit.recordAs(t.reviewer().id(), "REVIEW_NOTE", "program", programId, Map.of()));
        String from = Instant.now().minusSeconds(3600).toString();
        String to = Instant.now().plusSeconds(3600).toString();
        List<String> queries = List.of("", "?action=PROGRAM_CREATED", "?entityType=program", "?entityId=" + programId,
                "?userId=" + t.reviewer().id(), "?from=" + from + "&to=" + to, "?from=" + to, "?action=NO_SUCH_ACTION",
                "?entityType=program&userId=" + t.contributor().id() + "&from=" + from);
        for (String query : queries) {
            var list = mvc.get().uri("/api/v1/audit" + query + (query.isEmpty() ? "?limit=500" : "&limit=500")).with(user(t.admin())).exchange();
            List<String> expected = new ArrayList<>();
            json.readTree(list.getResponse().getContentAsString()).forEach((JsonNode e) ->
                    expected.add(e.get("action").asString() + "|" + e.get("entityType").asString() + "|"
                            + (e.get("entityId").isNull() ? "" : e.get("entityId").asString())));
            List<String> exported = rows(export(t.admin(), query)).stream()
                    .map(r -> r.get(2) + "|" + r.get(3) + "|" + r.get(4)).filter(s -> !s.startsWith("AUDIT_EXPORTED")).toList();
            assertThat(exported).as(query).containsExactlyElementsOf(expected.stream().filter(s -> !s.startsWith("AUDIT_EXPORTED")).toList());
        }
    }

    @Test
    void theExportIsAuditedWithItsFiltersAndRowCount() throws Exception {
        List<List<String>> rows = rows(export(t.admin(), "?action=PROGRAM_CREATED&entityType=program"));
        assertThat(rows).hasSize(1);

        List<AuditEntry> exports = exportsOf();
        assertThat(exports).hasSize(1);
        AuditEntry entry = exports.get(0);
        assertThat(entry.userId()).isEqualTo(t.admin().id());
        assertThat(entry.entityType()).isEqualTo("audit_log");
        assertThat(entry.entityId()).isNull();
        assertThat(entry.detail()).containsOnly(Map.entry("action", "PROGRAM_CREATED"), Map.entry("entityType", "program"),
                Map.entry("rowCount", 1), Map.entry("complete", true));

        List<List<String>> second = rows(export(t.admin(), "?action=AUDIT_EXPORTED"));
        assertThat(second).as("an export lists the earlier exports, not itself").hasSize(1);
        assertThat(exportsOf()).hasSize(2);
    }

    @Test
    void anExportOfNothingIsStillAFileWithAHeaderAndIsAudited() throws Exception {
        assertThat(rows(export(t.admin(), "?action=NO_SUCH_ACTION"))).isEmpty();
        assertThat(exportsOf().get(0).detail()).containsEntry("rowCount", 0).containsEntry("complete", true);
    }

    @Test
    void aValueTheDatabaseRefusesIsAnErrorAndNotAFile() throws Exception {
        for (String query : List.of("?action=a%00b", "?entityId=not-a-uuid", "?userId=not-a-uuid", "?from=yesterday", "?to=%00")) {
            var result = export(t.admin(), query);
            assertThat(result.getResponse().getStatus()).as(query).isEqualTo(400);
            assertThat(result.getResponse().getContentType()).as(query).contains("application/problem+json");
            assertThat(result.getResponse().getHeader("Content-Disposition")).as(query).isNull();
        }
        assertThat(exportsOf()).as("nothing left the system").isEmpty();
    }

    @Test
    void anotherTenantsEntriesAreNeverInTheFile() throws Exception {
        var other = tenants.create("auditexport-other");
        TenantContext.runAs(other.tenantId(), () -> audit.recordAs(other.admin().id(), "OTHER_TENANT_ONLY", "program", UUID.randomUUID(), Map.of()));
        assertThat(rows(export(t.admin(), "")).stream().map(r -> r.get(2))).doesNotContain("OTHER_TENANT_ONLY");
        assertThat(rows(export(t.admin(), "")).stream().map(r -> r.get(1))).doesNotContain(other.admin().displayName());
    }
}
