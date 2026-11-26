package com.lexvay.program;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramService {

    private final ProgramRepository programs;
    private final JurisdictionRepository jurisdictions;
    private final AuditService audit;

    public ProgramService(ProgramRepository programs, JurisdictionRepository jurisdictions, AuditService audit) {
        this.programs = programs;
        this.jurisdictions = jurisdictions;
        this.audit = audit;
    }

    @Transactional
    public Program create(CreateProgramRequest request) {
        var codes = new LinkedHashSet<>(request.jurisdictions());
        List<Jurisdiction> found = jurisdictions.findByCodes(codes);
        if (found.size() != codes.size()) {
            var known = found.stream().map(Jurisdiction::code).collect(Collectors.toSet());
            var unknown = codes.stream().filter(c -> !known.contains(c)).toList();
            throw new IllegalArgumentException("Unknown jurisdiction code(s): " + unknown);
        }
        UUID id = UUID.randomUUID();
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        programs.insert(id, tenantId, request.name().trim(), request.licenseType().trim(), request.catalogYear().trim(), actor);
        for (Jurisdiction j : found) {
            programs.insertJurisdiction(id, j.id(), tenantId, actor);
        }
        audit.record("PROGRAM_CREATED", "program", id, Map.of(
                "name", request.name().trim(),
                "licenseType", request.licenseType().trim(),
                "catalogYear", request.catalogYear().trim(),
                "jurisdictions", found.stream().map(Jurisdiction::code).toList()));
        return programs.findById(id).orElseThrow();
    }

    public Program get(UUID id) {
        return programs.findById(id).orElseThrow(() -> new NotFoundException("Program not found"));
    }

    public List<Program> list() {
        return programs.findAll();
    }
}
