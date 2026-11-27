package com.lexvay.program;

import com.lexvay.assessment.ProgramCitationService;
import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.TenantContext;
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
    private final ProgramCitationService citations;
    private final AuditService audit;

    public ProgramFactService(ProgramFactRepository facts, ProgramService programs, ProgramCitationService citations,
                              AuditService audit) {
        this.facts = facts;
        this.programs = programs;
        this.citations = citations;
        this.audit = audit;
    }

    @Transactional
    public ProgramFact create(UUID programId, CreateProgramFactRequest request) {
        programs.get(programId);
        UUID actor = CurrentUser.require().id();
        ProgramCitationService.Citation citation = citations.cite(programId, request.documentId(), request.pageNumber(),
                request.quote(), actor);
        UUID id = UUID.randomUUID();
        facts.insert(id, TenantContext.require(), programId, request.key(), request.value(), citation.spanId(), actor);
        audit.record("PROGRAM_FACT_RECORDED", "program_fact", id, Map.of(
                "programId", programId.toString(), "key", request.key().name(), "value", request.value().toPlainString(),
                "documentId", citation.document().id().toString(), "pageNumber", citation.page().pageNumber(),
                "quote", citation.match().exactText()));
        return facts.findByProgram(programId).stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ProgramFact> list(UUID programId) {
        programs.get(programId);
        return facts.findByProgram(programId);
    }
}
