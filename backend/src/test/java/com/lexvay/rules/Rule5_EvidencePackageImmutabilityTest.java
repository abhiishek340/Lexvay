package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.common.TenantContext;
import com.lexvay.review.ReviewFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for evidence packages: a package is finished once, as READY with its file or as FAILED, and never deleted. */
class Rule5_EvidencePackageImmutabilityTest extends AbstractAssessmentTest {

    private static final String SHA = "a".repeat(64);

    @Autowired JdbcClient jdbc;

    UUID determinationId;

    @BeforeEach
    void signed() {
        var rf = new ReviewFixtures(mvc, json, fx);
        var scenario = rf.californiaScenario(t, "immutable-package");
        rf.decideAll(t, scenario);
        determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), scenario.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
    }

    private UUID queued() {
        UUID id = UUID.randomUUID();
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("INSERT INTO evidence_package (id, tenant_id, determination_id) VALUES (:id, :t, :d)")
                .param("id", id).param("t", t.tenantId()).param("d", determinationId).update());
        return id;
    }

    private int update(UUID id, String sql) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql(sql).param("id", id).update());
    }

    private static final String READY = "UPDATE evidence_package SET status = 'READY', storage_key = 'k/p', content_sha256 = '" + SHA
            + "', size_bytes = 10 WHERE id = :id";

    @Test
    void aQueuedPackageIsFinishedOnceAsReady() {
        UUID id = queued();

        assertThat(update(id, READY)).isEqualTo(1);

        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET status = 'FAILED' WHERE id = :id")).hasMessageContaining("QUEUED to READY or FAILED");
        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET size_bytes = 11 WHERE id = :id")).hasMessageContaining("QUEUED to READY or FAILED");
    }

    @Test
    void aQueuedPackageCanFailButNotWithAFile() {
        UUID id = queued();

        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET status = 'FAILED', storage_key = 'k/p' WHERE id = :id"))
                .hasMessageContaining("evidence_package_file_iff_ready");
        assertThat(update(id, "UPDATE evidence_package SET status = 'FAILED' WHERE id = :id")).isEqualTo(1);
        assertThatThrownBy(() -> update(id, READY)).hasMessageContaining("QUEUED to READY or FAILED");
    }

    @Test
    void nothingBesidesTheFileColumnsAndStatusEverChanges() {
        UUID id = queued();

        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET status = 'FAILED', created_at = now() + interval '1 day' WHERE id = :id"))
                .hasMessageContaining("only the file columns and the status may change");
        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET status = 'QUEUED' WHERE id = :id")).hasMessageContaining("QUEUED to READY or FAILED");
    }

    @Test
    void aPackageCannotBeDeleted() {
        UUID id = queued();

        assertThatThrownBy(() -> update(id, "DELETE FROM evidence_package WHERE id = :id")).hasMessageContaining("immutable");
    }

    @Test
    void aReadyPackageNeedsItsFile() {
        UUID id = queued();

        assertThatThrownBy(() -> update(id, "UPDATE evidence_package SET status = 'READY' WHERE id = :id")).hasMessageContaining("evidence_package_file_iff_ready");
    }
}
