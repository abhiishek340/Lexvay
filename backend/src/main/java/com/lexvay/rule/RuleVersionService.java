package com.lexvay.rule;

import com.lexvay.audit.AuditService;
import com.lexvay.change.ChangeService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.document.Document;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.DocumentService;
import com.lexvay.document.FileType;
import com.lexvay.document.TextExtractor;
import com.lexvay.job.Job;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import com.lexvay.rule.source.QueuedObservation;
import com.lexvay.rule.source.RuleSourceRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class RuleVersionService {

    public record CreateResult(UUID ruleVersionId, UUID jobId) {
    }

    public record Approval(UUID by, OffsetDateTime at) {
    }

    public record Extraction(UUID jobId, String status, String error) {
    }

    /**
     * What the screens read. The legal status and dates are the current ones (D-552); {@code observedAt} is when the
     * text was acquired (null for an upload); {@code queuedObservations} are the changes seen after this draft was
     * made, oldest first, each with its stored document (D-555).
     */
    public record RuleVersionView(UUID id, String jurisdiction, String licenseType, String label, String sourceUrl,
                                  LegalStatus legalStatus, LocalDate effectiveFrom, LocalDate effectiveTo,
                                  LocalDate publishedAt, LocalDate adoptedAt, String datesBasis, OffsetDateTime observedAt,
                                  UUID supersedesId, UUID documentId, Approval approval, long requirementCount,
                                  Extraction extraction, List<QueuedObservation> queuedObservations, OffsetDateTime createdAt) {
    }

    public record RequirementsView(UUID ruleVersionId, boolean approved, List<Requirement> requirements) {
    }

    private final RuleVersionRepository ruleVersions;
    private final JurisdictionRepository jurisdictions;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final JobRepository jobs;
    private final AuditService audit;
    private final RequirementListParser parser;
    private final ChangeService changes;
    private final TextExtractor extractor;
    private final RuleSourceRepository ruleSources;

    public RuleVersionService(RuleVersionRepository ruleVersions, JurisdictionRepository jurisdictions,
                              DocumentRepository documents, DocumentService documentService, JobRepository jobs, AuditService audit,
                              RequirementListParser parser, ChangeService changes, TextExtractor extractor,
                              RuleSourceRepository ruleSources) {
        this.ruleSources = ruleSources;
        this.changes = changes;
        this.extractor = extractor;
        this.ruleVersions = ruleVersions;
        this.jurisdictions = jurisdictions;
        this.documents = documents;
        this.documentService = documentService;
        this.jobs = jobs;
        this.audit = audit;
        this.parser = parser;
    }

    /** A rule version to create, before its rule text exists as a document. */
    public record NewVersion(String jurisdiction, String licenseType, String label, String sourceUrl, LegalPosition legal,
                             OffsetDateTime observedAt, UUID supersedesId) {
    }

    /** Stores the rule text as a RULE_TEXT document with its page (D-044) and queues extraction. */
    @Transactional
    public CreateResult create(CreateRuleVersionRequest request) {
        NewVersion version = new NewVersion(request.jurisdiction(), request.licenseType().trim(), request.label().trim(),
                request.sourceUrl().trim(), request.position(), null, request.supersedesId());
        Jurisdiction jurisdiction = validate(version);
        byte[] bytes = request.text().getBytes(StandardCharsets.UTF_8);
        UUID documentId = documentService.storeRuleText(bytes, slug(request.label()) + "." + FileType.TXT.extension(),
                extractor.extract(FileType.TXT, bytes));
        return insert(jurisdiction, version, documentId);
    }

    /**
     * Creates a rule version for rule text that is already stored (a fetched rule source). Same validation, change
     * event and extraction job as {@link #create}; the caller may be the system, which has no signed-in user.
     */
    @Transactional
    public CreateResult createFromDocument(NewVersion version, UUID documentId) {
        return insert(validate(version), version, documentId);
    }

    private Jurisdiction validate(NewVersion version) {
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(version.jurisdiction())).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown jurisdiction code: " + version.jurisdiction()));
        if (version.supersedesId() != null) {
            RuleVersion previous = ruleVersions.findById(version.supersedesId())
                    .orElseThrow(() -> new IllegalArgumentException("Superseded rule version not found"));
            if (!previous.jurisdictionId().equals(jurisdiction.id()) || !previous.licenseType().equals(version.licenseType())) {
                throw new IllegalArgumentException("A rule version may only supersede one for the same jurisdiction and license type");
            }
        }
        return jurisdiction;
    }

    private CreateResult insert(Jurisdiction jurisdiction, NewVersion version, UUID documentId) {
        UUID id = UUID.randomUUID();
        ruleVersions.insert(id, TenantContext.require(), jurisdiction.id(), version.licenseType(), version.label(),
                version.sourceUrl(), version.legal(), version.observedAt(), version.supersedesId(), documentId,
                CurrentUser.idOrNull());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("jurisdiction", jurisdiction.code());
        detail.put("licenseType", version.licenseType());
        detail.put("label", version.label());
        detail.putAll(describe(version.legal()));
        putIfPresent(detail, "observedAt", version.observedAt());
        detail.put("documentId", documentId.toString());
        detail.put("supersedesId", version.supersedesId() == null ? "" : version.supersedesId().toString());
        audit.record("RULE_VERSION_CREATED", "rule_version", id, detail);
        if (version.supersedesId() != null) {
            changes.onRuleVersionSuperseded(version.supersedesId(), id, version.label());
        }
        UUID jobId = jobs.enqueue(JobType.EXTRACT_REQUIREMENTS, id, Map.of("ruleVersionId", id.toString()));
        return new CreateResult(id, jobId);
    }

    @Transactional(readOnly = true)
    public RuleVersion require(UUID id) {
        return ruleVersions.findById(id).orElseThrow(() -> new NotFoundException("Rule version not found"));
    }

    @Transactional(readOnly = true)
    public RuleVersionView view(UUID id) {
        RuleVersion version = require(id);
        return view(version, jobs.latest(JobType.EXTRACT_REQUIREMENTS, id), ruleSources.queuedObservations(List.of(id)));
    }

    @Transactional(readOnly = true)
    public List<RuleVersionView> list() {
        List<RuleVersion> all = ruleVersions.findAll();
        List<UUID> ids = all.stream().map(RuleVersion::id).toList();
        Map<UUID, Job> extractions = jobs.latestByEntity(JobType.EXTRACT_REQUIREMENTS, ids);
        Map<UUID, List<QueuedObservation>> queued = ruleSources.queuedObservations(ids);
        return all.stream().map(v -> view(v, Optional.ofNullable(extractions.get(v.id())), queued)).toList();
    }

    @Transactional(readOnly = true)
    public RequirementsView requirements(UUID id) {
        RuleVersion version = require(id);
        return new RequirementsView(id, version.approved(), ruleVersions.requirements(id));
    }

    /** Reviewer replaces the extracted list before approval (D-024). */
    @Transactional
    public RequirementsView replaceRequirements(UUID id, JsonNode body) {
        RuleVersion version = require(id);
        if (version.approved()) {
            throw new ConflictException("Requirements of an approved rule version cannot be changed");
        }
        List<RequirementDraft> drafts;
        try {
            drafts = parser.parse(body);
        } catch (InvalidRequirementListException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        List<Requirement> before = ruleVersions.requirements(id);
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();
        ruleVersions.deleteRequirements(id);
        ruleVersions.insertRequirements(tenantId, id, drafts, actor);
        List<Requirement> after = ruleVersions.requirements(id);
        audit.record("REQUIREMENTS_REPLACED", "rule_version", id, Map.of("before", summarize(before), "after", summarize(after)));
        return new RequirementsView(id, false, after);
    }

    /**
     * Approves the extracted requirements for assessment. A version can only be approved when a person has recorded that
     * it is final (IN_FORCE or FINAL_NOT_EFFECTIVE) with its effective date and the basis of the dates (D-553); a text
     * that was merely observed has none of these. The approval pins that status as a status row of its own.
     */
    @Transactional
    public RuleVersionView approveRequirements(UUID id) {
        RuleVersion version = require(id);
        if (version.approved()) {
            throw new ConflictException("Requirements are already approved");
        }
        long count = version.requirementCount();
        if (count == 0) {
            throw new ConflictException("There are no requirements to approve; extraction has not produced any");
        }
        LegalPosition legal = version.legal();
        if (!legal.status().approvable()) {
            throw new ConflictException("The legal status is " + legal.status() + ". Record IN_FORCE or FINAL_NOT_EFFECTIVE, "
                    + "with the dates and where they come from, before approving the requirements");
        }
        if (legal.effectiveFrom() == null) {
            // A date always comes with its basis (LegalPosition), so the missing effective date is the gap to name.
            throw new ConflictException("The effective date is not recorded. Record it, with where it comes from, before approving the requirements");
        }
        UUID actor = CurrentUser.require().id();
        ruleVersions.approve(id, actor);
        ruleVersions.insertStatus(TenantContext.require(), id, legal, actor);
        Map<String, Object> detail = new LinkedHashMap<>(describe(legal));
        detail.put("requirementCount", count);
        audit.record("REQUIREMENTS_APPROVED", "rule_version", id, detail);
        return view(id);
    }

    /** A person's correction of the legal status and dates; appended as a new row, never an edit (D-552). */
    @Transactional
    public RuleVersionView recordStatus(UUID id, RecordRuleStatusRequest request) {
        RuleVersion version = require(id);
        LegalPosition position = request.position();
        ruleVersions.insertStatus(TenantContext.require(), id, position, CurrentUser.require().id());
        Map<String, Object> detail = new LinkedHashMap<>(describe(position));
        detail.put("previousLegalStatus", version.legal().status().name());
        audit.record("RULE_STATUS_RECORDED", "rule_version", id, detail);
        return view(id);
    }

    /** Text of the rule, for extraction. */
    public String ruleText(RuleVersion version) {
        return documents.pages(version.documentId()).stream().map(p -> p.text()).collect(Collectors.joining("\n"));
    }

    private static RuleVersionView view(RuleVersion v, Optional<Job> extractionJob, Map<UUID, List<QueuedObservation>> queued) {
        Extraction extraction = extractionJob.map(j -> new Extraction(j.id(), j.status().name(), j.error())).orElse(null);
        LegalPosition legal = v.legal();
        return new RuleVersionView(v.id(), v.jurisdictionCode(), v.licenseType(), v.label(), v.sourceUrl(), legal.status(),
                legal.effectiveFrom(), legal.effectiveTo(), legal.publishedAt(), legal.adoptedAt(), legal.datesBasis(),
                v.observedAt(), v.supersedesId(), v.documentId(), v.approved() ? new Approval(v.approvedBy(), v.approvedAt()) : null,
                v.requirementCount(), extraction, queued.getOrDefault(v.id(), List.of()), v.createdAt());
    }

    /** The audited form of a legal position: the status always, a date or the basis only when it is recorded. */
    private static Map<String, Object> describe(LegalPosition legal) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("legalStatus", legal.status().name());
        putIfPresent(detail, "effectiveFrom", legal.effectiveFrom());
        putIfPresent(detail, "effectiveTo", legal.effectiveTo());
        putIfPresent(detail, "publishedAt", legal.publishedAt());
        putIfPresent(detail, "adoptedAt", legal.adoptedAt());
        putIfPresent(detail, "datesBasis", legal.datesBasis());
        return detail;
    }

    private static void putIfPresent(Map<String, Object> detail, String key, Object value) {
        if (value != null) {
            detail.put(key, value.toString());
        }
    }

    private static List<Map<String, Object>> summarize(List<Requirement> requirements) {
        return requirements.stream().<Map<String, Object>>map(r -> Map.of(
                "ref", r.ref(), "title", r.title(), "text", r.text(), "type", r.type().name(),
                "numericField", r.numericField() == null ? "" : r.numericField().name(),
                "numericOperator", r.numericOperator() == null ? "" : r.numericOperator(),
                "numericValue", r.numericValue() == null ? "" : r.numericValue().toPlainString(),
                "searchTerms", r.searchTerms())).toList();
    }

    public static String slug(String label) {
        String slug = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "rule-text" : slug;
    }

    Document ruleDocument(RuleVersion version) {
        return documents.findById(version.documentId()).orElseThrow();
    }
}
