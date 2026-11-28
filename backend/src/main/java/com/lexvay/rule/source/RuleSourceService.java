package com.lexvay.rule.source;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.tenant.AppUserRepository;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final AppUserRepository users;
    private final UrlPolicy urlPolicy;
    private final AuditService audit;
    private final Clock clock;

    public RuleSourceService(RuleSourceRepository sources, JurisdictionRepository jurisdictions, AppUserRepository users,
                             UrlPolicy urlPolicy, AuditService audit, Clock clock) {
        this.sources = sources;
        this.jurisdictions = jurisdictions;
        this.users = users;
        this.urlPolicy = urlPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Registers a source after the {@link UrlPolicy} accepts its address, and queues its first check. A person
     * registering the source is a person asking for that check, so it runs although access is not yet reviewed.
     */
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
     * addresses that do not exist and stay inert until a person runs a check (D-186). Not reachable from the API. A new
     * source may be fetched from its own host only, and its access is not reviewed.
     */
    @Transactional
    public UUID register(CreateRuleSourceRequest request) {
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(request.jurisdictionCode())).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown jurisdiction code: " + request.jurisdictionCode()));
        String host = FetchScope.hostOf(URI.create(request.url()));
        if (host.isEmpty()) {
            throw new IllegalArgumentException("The address has no host");
        }
        UUID id = UUID.randomUUID();
        String licenseType = request.licenseType().trim();
        String label = request.label().trim();
        sources.insert(id, TenantContext.require(), jurisdiction.id(), licenseType, label, request.url(),
                request.checkIntervalHours(), SourceGovernance.initial(host), CurrentUser.idOrNull());
        audit.record("RULE_SOURCE_CREATED", "rule_source", id, Map.of(
                "jurisdiction", jurisdiction.code(), "licenseType", licenseType, "label", label, "url", request.url(),
                "checkIntervalHours", request.checkIntervalHours()));
        return id;
    }

    @Transactional(readOnly = true)
    public List<RuleSourceView> list() {
        return sources.list(OffsetDateTime.now(clock));
    }

    /** The jurisdictions in which a source of the licence type is not HEALTHY, so their rules may be out of date (D-573). */
    @Transactional(readOnly = true)
    public Set<UUID> jurisdictionsWithUnhealthySource(Collection<UUID> jurisdictionIds, String licenseType) {
        return sources.jurisdictionsWithUnhealthySource(jurisdictionIds, licenseType, OffsetDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<RuleSourceCheck> checks(UUID id, int limit) {
        return sources.checks(id, limit).orElseThrow(() -> new NotFoundException("Rule source not found"));
    }

    /**
     * A person asks for a check now, whether or not the source is due or active, and whether or not its access was
     * reviewed. A source whose access is RESTRICTED or BLOCKED is never fetched, so no check is queued.
     */
    @Transactional
    public CheckQueued checkNow(UUID id) {
        RuleSource source = sources.findById(id).orElseThrow(() -> new NotFoundException("Rule source not found"));
        AccessStatus access = source.governance().accessStatus();
        if (access.neverFetched()) {
            throw new ConflictException("This rule source is " + access + ": it is never fetched");
        }
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

    /**
     * Replaces the governance of a source (RG-2). The source's own address must stay within what is allowed, or the
     * source could never be fetched; the owner must be an active user of this tenant. The audit entry carries the
     * governance before and after.
     */
    @Transactional
    public SourceGovernance updateGovernance(UUID id, GovernanceRequest request) {
        RuleSource source = sources.lockById(id).orElseThrow(() -> new NotFoundException("Rule source not found"));
        SourceGovernance after = request.governance();
        URI url = URI.create(source.url());
        if (!after.allowedHosts().contains(FetchScope.hostOf(url))) {
            throw new IllegalArgumentException("The allowed hosts must include " + FetchScope.hostOf(url) + ", the host of this source");
        }
        if (!after.allowedPathPrefixes().isEmpty() && after.allowedPathPrefixes().stream().noneMatch(FetchScope.path(url)::startsWith)) {
            throw new IllegalArgumentException("The allowed paths must include a prefix of " + FetchScope.path(url) + ", the path of this source");
        }
        if (after.ownerUserId() != null && users.findAccount(after.ownerUserId()).filter(AppUserRepository.Account::active).isEmpty()) {
            throw new IllegalArgumentException("The owner must be an active user of this organisation");
        }
        sources.updateGovernance(id, after);
        audit.record("RULE_SOURCE_GOVERNANCE_CHANGED", "rule_source", id,
                Map.of("before", describe(source.governance()), "after", describe(after)));
        return after;
    }

    private static Map<String, Object> describe(SourceGovernance governance) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("authorityClass", governance.authorityClass().name());
        described.put("allowedHosts", governance.allowedHosts());
        described.put("allowedPathPrefixes", governance.allowedPathPrefixes());
        described.put("expectedIdentifiers", governance.expectedIdentifiers());
        described.put("accessStatus", governance.accessStatus().name());
        described.put("accessNote", governance.accessNote());
        described.put("ownerUserId", governance.ownerUserId() == null ? null : governance.ownerUserId().toString());
        return described;
    }
}
