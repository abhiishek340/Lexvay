package com.lexvay.determination.register;

import com.lexvay.audit.AuditService;
import com.lexvay.common.NotFoundException;
import com.lexvay.tenant.TenantRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The determination register: what people signed, as signed (F8). It reports signatures and their absence and
 * never infers an outcome (product rule 1).
 */
@Service
public class RegisterService {

    private final RegisterRepository repository;
    private final AuditService audit;
    private final TenantRepository tenants;

    public RegisterService(RegisterRepository repository, AuditService audit, TenantRepository tenants) {
        this.repository = repository;
        this.audit = audit;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<RegisterRow> register(RegisterFilter filter) {
        return repository.register(filter);
    }

    @Transactional(readOnly = true)
    public DeterminationLists lists(UUID programId) {
        return repository.lists(programId).orElseThrow(() -> new NotFoundException("Program not found"));
    }

    /**
     * The register as CSV. Signed records leaving the system is an action of its own, so the export is audited
     * (REGISTER_EXPORTED with the filters and the row count) in the same transaction that reads the rows.
     */
    @Transactional
    public byte[] exportCsv(RegisterFilter filter) {
        List<RegisterRow> rows = repository.register(filter);
        Map<String, Object> detail = new LinkedHashMap<>(filter.auditDetail());
        detail.put("rowCount", rows.size());
        audit.record("REGISTER_EXPORTED", "determination_register", null, detail);
        return RegisterCsv.write(rows, tenants.currentDataMode());
    }
}
