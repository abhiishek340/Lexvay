package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.document.DocumentOcrCheckpoints;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.OcrPage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 7 for OCR checkpoints: another tenant, or no tenant, sees and writes none of them. */
class Rule7_OcrCheckpointIsolationTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired DocumentRepository documents;
    @Autowired DocumentOcrCheckpoints checkpoints;
    @Autowired JdbcClient jdbc;

    TestTenants.TenantWithUsers a;
    TestTenants.TenantWithUsers b;
    UUID documentOfA;

    @BeforeEach
    void setUp() {
        a = tenants.create("ocr-a");
        b = tenants.create("ocr-b");
        documentOfA = fixtures.insertChain(a.tenantId(), a.admin().id()).documentId();
        TenantContext.runAs(a.tenantId(), () -> checkpoints.forDocument(a.tenantId(), documentOfA)
                .save(1, new OcrPage("Tenant A's scanned page", 90), 300));
    }

    private long count() {
        return jdbc.sql("SELECT count(*) FROM document_ocr_page").query(Long.class).single();
    }

    @Test
    void anotherTenantSeesNoCheckpointOfThisOne() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThat(count()).isZero();
            assertThat(documents.ocrCheckpoints(documentOfA)).isEmpty();
            assertThat(checkpoints.forDocument(b.tenantId(), documentOfA).find(1, 300)).isEmpty();
            return null;
        });
        TenantContext.runAs(a.tenantId(), () -> {
            assertThat(documents.ocrCheckpoints(documentOfA)).hasSize(1);
            return null;
        });
    }

    @Test
    void aTenantCannotWriteACheckpointForAnotherTenant() {
        TenantContext.runAs(b.tenantId(), () -> {
            assertThatThrownBy(() -> documents.insertOcrCheckpoint(a.tenantId(), documentOfA, 2, new OcrPage("Forged", 90), 300))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> checkpoints.forDocument(a.tenantId(), documentOfA).save(2, new OcrPage("Forged", 90), 300))
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
        assertThat(TenantContext.runAs(a.tenantId(), () -> documents.ocrCheckpoints(documentOfA))).hasSize(1);
    }

    @Test
    void noTenantFailsClosed() {
        TenantContext.runWithoutTenant(() -> {
            assertThat(count()).isZero();
            assertThatThrownBy(() -> documents.insertOcrCheckpoint(a.tenantId(), documentOfA, 2, new OcrPage("Forged", 90), 300))
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
    }
}
