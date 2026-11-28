package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** SEC-3: GET /api/v1/audit/verify, for administrators, and the audit entry each check leaves. */
class AuditVerificationApiTest extends AbstractAuditSealTest {

    @Autowired MockMvcTester mvc;
    @Autowired AuditRepository repository;
    @Autowired ObjectMapper json;

    TestTenants.TenantWithUsers t;

    @BeforeEach
    void setUp() {
        t = tenants.create("verify");
        record(t.tenantId(), 12);
    }

    private MvcTestResult verifyAs(LexvayUser as) {
        var request = mvc.get().uri("/api/v1/audit/verify");
        return (as == null ? request : request.with(user(as))).exchange();
    }

    private JsonNode body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatus(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private List<AuditEntry> verifications() {
        return TenantContext.runAs(t.tenantId(), () -> repository.newestFirst(
                new AuditFilter("AUDIT_VERIFIED", null, null, null, null, null), 100, 0));
    }

    @Test
    void onlyAdministratorsMayVerify() {
        for (LexvayUser who : List.of(t.contributor(), t.reviewer(), t.signer())) {
            assertThat(verifyAs(who)).hasStatus(403);
        }
        assertThat(verifyAs(null)).hasStatus(401);
        assertThat(verifications()).isEmpty();
    }

    @Test
    void anIntactTrailIsReportedWithItsHeadAndIsAudited() throws Exception {
        sealAll(t.tenantId());

        JsonNode report = body(verifyAs(t.admin()));

        long sealed = inTenant(t.tenantId(), Long.class, "SELECT count(*) FROM audit_seal");
        assertThat(report.get("intact").asBoolean()).isTrue();
        assertThat(report.get("sealed").asLong()).isEqualTo(sealed);
        assertThat(report.get("pending").asLong()).isZero();
        assertThat(report.get("firstBrokenSeq").isNull()).isTrue();
        assertThat(report.get("firstBrokenReason").isNull()).isTrue();
        assertThat(report.get("headHash").asString()).matches("[0-9a-f]{64}");
        assertThat(report.get("lastSealedAt").isNull()).isFalse();

        assertThat(verifications()).singleElement().satisfies(entry -> {
            assertThat(entry.userId()).isEqualTo(t.admin().id());
            assertThat(entry.entityType()).isEqualTo("audit_log");
            assertThat(entry.entityId()).isNull();
            assertThat(entry.detail()).containsOnlyKeys("intact", "sealed", "headHash")
                    .containsEntry("intact", true).containsEntry("sealed", (int) sealed)
                    .containsEntry("headHash", report.get("headHash").asString());
        });
    }

    @Test
    void entriesNotYetSealedArePendingAndTheCheckItselfJoinsThem() throws Exception {
        JsonNode first = body(verifyAs(t.admin()));
        assertThat(first.get("sealed").asLong()).isZero();
        assertThat(first.get("pending").asLong()).isEqualTo(inTenant(t.tenantId(), Long.class, "SELECT count(*) FROM audit_log") - 1);
        assertThat(first.get("intact").asBoolean()).as("nothing sealed is not broken").isTrue();
        assertThat(first.get("headHash").isNull()).isTrue();
        assertThat(first.get("lastSealedAt").isNull()).isTrue();

        JsonNode second = body(verifyAs(t.admin()));
        assertThat(second.get("pending").asLong()).isEqualTo(first.get("pending").asLong() + 1);
    }

    @Test
    void aBrokenTrailNamesTheFirstBrokenEntryAndTheAuditEntryRecordsIt() throws Exception {
        sealAll(t.tenantId());
        asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET detail = '{\"n\": 0}'::jsonb WHERE id = ?",
                auditIdOfSeq(t.tenantId(), 5));

        JsonNode report = body(verifyAs(t.admin()));

        assertThat(report.get("intact").asBoolean()).isFalse();
        assertThat(report.get("firstBrokenSeq").asLong()).isEqualTo(5);
        assertThat(report.get("firstBrokenReason").asString()).contains("no longer matches its seal");
        assertThat(verifications().get(0).detail()).containsEntry("intact", false).containsEntry("firstBrokenSeq", 5);
    }

    @Test
    void anotherTenantsTrailIsNeverRead() throws Exception {
        TestTenants.TenantWithUsers other = tenants.create("verify-other");
        record(other.tenantId(), 30);
        sealer.sealPending();
        UUID victim = auditIdOfSeq(other.tenantId(), 3);
        asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET detail = '{\"n\": 0}'::jsonb WHERE id = ?", victim);

        JsonNode mine = body(verifyAs(t.admin()));
        assertThat(mine.get("intact").asBoolean()).as("the other tenant's break is not mine").isTrue();
        assertThat(mine.get("sealed").asLong()).isEqualTo(inTenant(t.tenantId(), Long.class, "SELECT count(*) FROM audit_seal"));

        JsonNode theirs = body(verifyAs(other.admin()));
        assertThat(theirs.get("intact").asBoolean()).isFalse();
        assertThat(theirs.get("firstBrokenSeq").asLong()).isEqualTo(3);
    }
}
