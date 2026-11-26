package com.lexvay.program;

import com.lexvay.assessment.EvidenceSpanRepository;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.TenantContext;
import com.lexvay.document.Document;
import com.lexvay.document.DocumentPage;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.QuoteVerifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Numbers the rules engine compares (D-006). Every fact cites a verified quote. */
@Service
public class ProgramFactService {

    private final ProgramFactRepository facts;
    private final ProgramService programs;
    private final DocumentRepository documents;
    private final EvidenceSpanRepository spans;
    private final AuditService audit;

    public ProgramFactService(ProgramFactRepository facts, ProgramService programs, DocumentRepository documents,
                              EvidenceSpanRepository spans, AuditService audit) {
        this.facts = facts;
        this.programs = programs;
        this.documents = documents;
        this.spans = spans;
        this.audit = audit;
    }

    @Transactional
    public ProgramFact create(UUID programId, CreateProgramFactRequest request) {
        programs.get(programId);
        Document document = documents.findById(request.documentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!programId.equals(document.programId())) {
            throw new IllegalArgumentException("Document belongs to a different program");
        }
        DocumentPage page = documents.page(document.id(), request.pageNumber())
                .orElseThrow(() -> new IllegalArgumentException("Page " + request.pageNumber() + " does not exist in this document"));
        QuoteVerifier.Match match = QuoteVerifier.find(page.text(), request.quote())
                .orElseThrow(() -> new IllegalArgumentException("The quote was not found on page " + request.pageNumber()
                        + " of " + document.filename()));
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        UUID spanId = spans.insert(tenantId, document.id(), page.pageNumber(), match.charStart(), match.charEnd(), match.exactText(), actor);
        UUID id = UUID.randomUUID();
        facts.insert(id, tenantId, programId, request.key(), request.value(), spanId, actor);
        audit.record("PROGRAM_FACT_RECORDED", "program_fact", id, Map.of(
                "programId", programId.toString(), "key", request.key().name(), "value", request.value().toPlainString(),
                "documentId", document.id().toString(), "pageNumber", page.pageNumber(), "quote", match.exactText()));
        return facts.findByProgram(programId).stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ProgramFact> list(UUID programId) {
        programs.get(programId);
        return facts.findByProgram(programId);
    }
}
