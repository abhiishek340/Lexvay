package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rules 5 and 6: immutable rows reject updates and deletes at the database. */
class Rule5_ImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired JdbcClient jdbc;

    SchemaFixtures.Chain chain;

    @BeforeEach
    void setUp() {
        var t = tenants.create("immutable");
        chain = fixtures.insertChain(t.tenantId(), t.admin().id());
    }

    private UUID idOf(String table) {
        return switch (table) {
            case "document" -> chain.documentId();
            case "document_page" -> chain.pageId();
            case "course_version" -> chain.courseVersionId();
            case "evidence_span" -> chain.evidenceSpanId();
            case "review_event" -> chain.reviewEventId();
            case "evidence_response" -> chain.evidenceResponseId();
            case "audit_log" -> chain.auditId();
            case "llm_call" -> chain.llmCallId();
            case "rule_version" -> chain.ruleVersionId();
            case "determination" -> chain.determinationId();
            default -> throw new IllegalArgumentException(table);
        };
    }

    @ParameterizedTest(name = "{0}.{1} cannot be updated")
    @CsvSource({
            "document, filename", "document_page, text", "course_version, catalog_year", "evidence_span, quote",
            "review_event, note", "evidence_response, comment", "audit_log, action", "llm_call, model", "rule_version, label", "determination, outcome"
    })
    void updateIsRejected(String table, String column) {
        String value = table.equals("determination") ? "DOES_NOT_MEET" : "changed";
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("UPDATE " + table + " SET " + column + " = :v WHERE id = :id")
                        .param("v", value).param("id", idOf(table)).update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Lexvay");
    }

    @ParameterizedTest(name = "{0} cannot be deleted")
    @CsvSource({"document_page", "evidence_span", "review_event", "evidence_response", "audit_log", "llm_call", "determination", "rule_version"})
    void deleteIsRejected(String table) {
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("DELETE FROM " + table + " WHERE id = :id").param("id", idOf(table)).update()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Lexvay");
    }

    @Test
    void determinationStatusMayChangeButNothingElse() {
        int updated = TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("UPDATE determination SET status = 'REOPENED' WHERE id = :id").param("id", chain.determinationId()).update());
        assertThat(updated).isEqualTo(1);
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("UPDATE determination SET status = 'SIGNED', versions_hash = :h WHERE id = :id")
                        .param("h", "d".repeat(64)).param("id", chain.determinationId()).update()))
                .hasMessageContaining("only status may change");
    }

    @Test
    void ruleVersionCanBeApprovedOnceAndNeverUnapproved() {
        TenantContext.runAs(chain.tenantId(), () -> {
            int updated = jdbc.sql("UPDATE rule_version SET requirements_approved_by = :u, requirements_approved_at = now() WHERE id = :id")
                    .param("u", chain.userId()).param("id", chain.ruleVersionId()).update();
            assertThat(updated).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.sql("UPDATE rule_version SET requirements_approved_by = NULL, requirements_approved_at = NULL WHERE id = :id")
                    .param("id", chain.ruleVersionId()).update()).hasMessageContaining("already approved");
            assertThatThrownBy(() -> jdbc.sql("UPDATE requirement SET title = 'Edited' WHERE id = :id")
                    .param("id", chain.requirementId()).update()).hasMessageContaining("approved rule version are immutable");
            assertThatThrownBy(() -> jdbc.sql("DELETE FROM requirement WHERE id = :id")
                    .param("id", chain.requirementId()).update()).hasMessageContaining("approved rule version are immutable");
        });
    }

    @Test
    void requirementsOfAnUnapprovedRuleVersionCanBeReplaced() {
        TenantContext.runAs(chain.tenantId(), () -> {
            int updated = jdbc.sql("UPDATE requirement SET title = 'Edited before approval' WHERE id = :id")
                    .param("id", chain.requirementId()).update();
            assertThat(updated).isEqualTo(1);
        });
    }

    @Test
    void gapConfirmedWithoutNoteIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("INSERT INTO review_event (tenant_id, mapping_id, kind, note, user_id) VALUES (:t, :m, 'GAP_CONFIRMED', '   ', :u)")
                        .param("t", chain.tenantId()).param("m", chain.mappingId()).param("u", chain.userId()).update()))
                .hasMessageContaining("gap_confirmed_requires_note");
    }

    @Test
    void mappingFindingIsLimitedToTheThreeProposedValues() {
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("UPDATE mapping SET finding = 'CONFIRMED_GAP' WHERE id = :id").param("id", chain.mappingId()).update()))
                .hasMessageContaining("mapping_finding_check");
        assertThatThrownBy(() -> TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("UPDATE mapping SET finding = 'EVIDENCE_FOUND', evidence_span_id = NULL WHERE id = :id")
                        .param("id", chain.mappingId()).update()))
                .hasMessageContaining("mapping_evidence_requires_span");
        Map<String, Object> row = TenantContext.runAs(chain.tenantId(), () ->
                jdbc.sql("SELECT finding FROM mapping WHERE id = :id").param("id", chain.mappingId()).query().singleRow());
        assertThat(row.get("finding")).isEqualTo("EVIDENCE_FOUND");
    }
}
