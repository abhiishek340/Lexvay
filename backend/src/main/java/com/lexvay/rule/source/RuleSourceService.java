package com.lexvay.rule.source;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobType;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers and manages the rule sources a tenant monitors; the checks themselves run as jobs. */
@Service
public class RuleSourceService {

    public record Created(UUID id, UUID jobId) {
    }

    public record CheckQueued(UUID jobId) {
    }

    public record Activation(UUID id, boolean active) {
    }

    private final RuleSourceRepository sources;
    private final JurisdictionRepository jurisdictions;
    private final UrlPolicy urlPolicy;
    private final AuditService audit;

    public RuleSourceService(RuleSourceRepository sources, JurisdictionRepository jurisdictions, UrlPolicy urlPolicy,
                             AuditService audit) {
        this.sources = sources;
        this.jurisdictions = jurisdictions;
        this.urlPolicy = urlPolicy;
        this.audit = audit;
    }

    /** Registers a source after the {@link UrlPolicy} accepts its address, and queues its first check. */
    @Transactional
    public Created create(CreateRuleSourceRequest request) {
        try {
            urlPolicy.requireAllowed(URI.create(request.url()));
        } catch (FetchRejected e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        UUID id = register(request);
        UUID jobId = sources.enqueueCheck(id, CurrentUser.idOrNull()).orElseThrow();
        return new Created(id, jobId);
    }

    /**
     * Registers a source without resolving its host or queueing a check. For the demo seed, whose sources point at
     * addresses that do not exist and stay inert until a person runs a check (D-186). Not reachable from the API.
     */
    @Transactional
    public UUID register(CreateRuleSourceRequest request) {
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(request.jurisdictionCode())).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown jurisdiction code: " + request.jurisdictionCode()));
        UUID id = UUID.randomUUID();
        String licenseType = request.licenseType().trim();
        String label = request.label().trim();
        sources.insert(id, TenantContext.require(), jurisdiction.id(), licenseType, label, request.url(),
                request.checkIntervalHours(), CurrentUser.idOrNull());
        audit.record("RULE_SOURCE_CREATED", "rule_source", id, Map.of(
                "jurisdiction", jurisdiction.code(), "licenseType", licenseType, "label", label, "url", request.url(),
                "checkIntervalHours", request.checkIntervalHours()));
        return id;
    }

    public List<RuleSourceView> list() {
        return sources.list();
    }

    public List<RuleSourceCheck> checks(UUID id, int limit) {
        return sources.checks(id, limit).orElseThrow(() -> new NotFoundException("Rule source not found"));
    }

    /** A person asks for a check now, whether or not the source is due or active. */
    @Transactional
    public CheckQueued checkNow(UUID id) {
        sources.findById(id).orElseThrow(() -> new NotFoundException("Rule source not found"));
        UUID jobId = sources.enqueueCheck(id, CurrentUser.idOrNull())
                .orElseThrow(() -> new ConflictException("A check of this rule source is already queued or running"));
        audit.record("RULE_SOURCE_CHECK_REQUESTED", "rule_source", id, Map.of("jobId", jobId.toString()));
        return new CheckQueued(jobId);
    }

    @Transactional
    public Activation setActive(UUID id, boolean active) {
        if (!sources.setActive(id, active)) {
            throw new NotFoundException("Rule source not found");
        }
        audit.record("RULE_SOURCE_ACTIVE_CHANGED", "rule_source", id, Map.of("active", active));
        return new Activation(id, active);
    }
}
