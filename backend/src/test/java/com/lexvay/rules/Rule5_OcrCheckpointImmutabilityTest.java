package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.SchemaFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.OcrPage;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Product rule 5 for OCR checkpoints: they record how a page's text was obtained and are never changed or removed. */
class Rule5_OcrCheckpointImmutabilityTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired SchemaFixtures fixtures;
    @Autowired DocumentRepository documents;
    @Autowired JdbcClient jdbc;

    UUID tenantId;
    UUID documentId;

    @BeforeEach
    void setUp() {
        var t = tenants.create("ocr-immutable");
        tenantId = t.tenantId();
        documentId = fixtures.insertChain(tenantId, t.admin().id()).documentId();
        TenantContext.runAs(tenantId, () -> documents.insertOcrCheckpoint(tenantId, documentId, 1, new OcrPage("Read by OCR", 90), 300));
    }

    @ParameterizedTest(name = "UPDATE document_ocr_page SET {0}")
    @ValueSource(strings = {"text = 'changed'", "mean_confidence = 99", "render_dpi = 150", "page_number = 2"})
    void updateIsRejected(String assignment) {
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> jdbc.sql(
                "UPDATE document_ocr_page SET " + assignment + " WHERE document_id = :d").param("d", documentId).update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Lexvay");
    }

    @Test
    void deleteIsRejected() {
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> jdbc.sql(
                "DELETE FROM document_ocr_page WHERE document_id = :d").param("d", documentId).update()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("Lexvay");
        assertThat(TenantContext.runAs(tenantId, () -> documents.ocrCheckpoints(documentId))).hasSize(1);
    }

    @Test
    void aSecondCheckpointOfThePageIsIgnoredNotAnError() {
        int inserted = TenantContext.runAs(tenantId, () -> documents.insertOcrCheckpoint(tenantId, documentId, 1, new OcrPage("Other", 10), 150));
        assertThat(inserted).isZero();
        assertThat(TenantContext.runAs(tenantId, () -> documents.ocrCheckpoints(documentId)))
                .containsExactly(new DocumentRepository.OcrCheckpoint(1, new OcrPage("Read by OCR", 90), 300));
    }
}
