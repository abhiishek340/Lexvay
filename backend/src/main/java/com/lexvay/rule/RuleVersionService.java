package com.lexvay.rule;

import com.lexvay.audit.AuditService;
import com.lexvay.change.ChangeService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.document.Document;
import com.lexvay.document.DocumentKind;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.DocumentService;
import com.lexvay.document.DuplicateDocumentException;
import com.lexvay.document.ExtractedPage;
import com.lexvay.document.FileStore;
import com.lexvay.document.FileType;
import com.lexvay.document.TextExtractor;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class RuleVersionService {

    public record CreateResult(UUID ruleVersionId, UUID jobId) {
    }

    public record Approval(UUID by, java.time.OffsetDateTime at) {
    }

    public record Extraction(UUID jobId, String status, String error) {
    }

    public record RuleVersionView(UUID id, String jurisdiction, String licenseType, String label, String sourceUrl,
                                  java.time.LocalDate effectiveFrom, UUID supersedesId, UUID documentId,
                                  Approval approval, long requirementCount, Extraction extraction,
                                  java.time.OffsetDateTime createdAt) {
    }

    public record RequirementsView(UUID ruleVersionId, boolean approved, List<Requirement> requirements) {
    }

    private final RuleVersionRepository ruleVersions;
    private final JurisdictionRepository jurisdictions;
    private final DocumentRepository documents;
    private final FileStore files;
    private final JobRepository jobs;
    private final AuditService audit;
    private final RequirementListParser parser;
    private final ChangeService changes;
    private final TextExtractor extractor;

    public RuleVersionService(RuleVersionRepository ruleVersions, JurisdictionRepository jurisdictions,
                              DocumentRepository documents, FileStore files, JobRepository jobs, AuditService audit,
                              RequirementListParser parser, ChangeService changes, TextExtractor extractor) {
        this.changes = changes;
        this.extractor = extractor;
        this.ruleVersions = ruleVersions;
        this.jurisdictions = jurisdictions;
        this.documents = documents;
        this.files = files;
        this.jobs = jobs;
        this.audit = audit;
        this.parser = parser;
    }

    /** Stores the rule text as a RULE_TEXT document with its page (D-044) and queues extraction. */
    @Transactional
    public CreateResult create(CreateRuleVersionRequest request) {
        Jurisdiction jurisdiction = jurisdictions.findByCodes(List.of(request.jurisdiction())).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown jurisdiction code: " + request.jurisdiction()));
        String licenseType = request.licenseType().trim();
        if (request.supersedesId() != null) {
            RuleVersion previous = ruleVersions.findById(request.supersedesId())
                    .orElseThrow(() -> new IllegalArgumentException("Superseded rule version not found"));
            if (!previous.jurisdictionId().equals(jurisdiction.id()) || !previous.licenseType().equals(licenseType)) {
                throw new IllegalArgumentException("A rule version may only supersede one for the same jurisdiction and license type");
            }
        }
        UUID tenantId = TenantContext.require();
        UUID actor = CurrentUser.require().id();

        byte[] bytes = request.text().getBytes(StandardCharsets.UTF_8);
        List<ExtractedPage> pages = extractor.extract(FileType.TXT, bytes);
        String sha256 = DocumentService.sha256(bytes);
        documents.findDuplicate(null, sha256).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.id());
        });
        UUID documentId = UUID.randomUUID();
        String storageKey = tenantId + "/" + documentId;
        files.put(storageKey, bytes);
        documents.insert(documentId, tenantId, null, null, slug(request.label()) + ".txt", sha256, storageKey,
                DocumentKind.RULE_TEXT, actor);
        for (ExtractedPage page : pages) {
            documents.insertPage(tenantId, documentId, page, actor);
        }

        UUID id = UUID.randomUUID();
        ruleVersions.insert(id, tenantId, jurisdiction.id(), licenseType, request.label().trim(), request.sourceUrl().trim(),
                request.effectiveFrom(), request.supersedesId(), documentId, actor);
        audit.record("RULE_VERSION_CREATED", "rule_version", id, Map.of(
                "jurisdiction", jurisdiction.code(), "licenseType", licenseType, "label", request.label().trim(),
                "effectiveFrom", request.effectiveFrom().toString(), "documentId", documentId.toString(),
                "supersedesId", request.supersedesId() == null ? "" : request.supersedesId().toString()));
        if (request.supersedesId() != null) {
            changes.onRuleVersionSuperseded(request.supersedesId(), id, request.label().trim());
        }
        UUID jobId = jobs.enqueue(JobType.EXTRACT_REQUIREMENTS, Map.of("ruleVersionId", id.toString()));
        return new CreateResult(id, jobId);
    }

    public RuleVersion require(UUID id) {
        return ruleVersions.findById(id).orElseThrow(() -> new NotFoundException("Rule version not found"));
    }

    public RuleVersionView view(UUID id) {
        return view(require(id));
    }

    public List<RuleVersionView> list() {
        return ruleVersions.findAll().stream().map(this::view).toList();
    }

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
        for (RequirementDraft draft : drafts) {
            ruleVersions.insertRequirement(tenantId, id, draft, actor);
        }
        List<Requirement> after = ruleVersions.requirements(id);
        audit.record("REQUIREMENTS_REPLACED", "rule_version", id, Map.of("before", summarize(before), "after", summarize(after)));
        return new RequirementsView(id, false, after);
    }

    @Transactional
    public RuleVersionView approveRequirements(UUID id) {
        RuleVersion version = require(id);
        if (version.approved()) {
            throw new ConflictException("Requirements are already approved");
        }
        long count = ruleVersions.countRequirements(id);
        if (count == 0) {
            throw new ConflictException("There are no requirements to approve; extraction has not produced any");
        }
        UUID actor = CurrentUser.require().id();
        ruleVersions.approve(id, actor);
        audit.record("REQUIREMENTS_APPROVED", "rule_version", id, Map.of("requirementCount", count));
        return view(id);
    }

    /** Text of the rule, for extraction. */
    public String ruleText(RuleVersion version) {
        return documents.pages(version.documentId()).stream().map(p -> p.text()).collect(Collectors.joining("\n"));
    }

    private RuleVersionView view(RuleVersion v) {
        Extraction extraction = jobs.latest(JobType.EXTRACT_REQUIREMENTS, "ruleVersionId", v.id())
                .map(j -> new Extraction(j.id(), j.status().name(), j.error())).orElse(null);
        return new RuleVersionView(v.id(), v.jurisdictionCode(), v.licenseType(), v.label(), v.sourceUrl(), v.effectiveFrom(),
                v.supersedesId(), v.documentId(), v.approved() ? new Approval(v.approvedBy(), v.approvedAt()) : null,
                ruleVersions.countRequirements(v.id()), extraction, v.createdAt());
    }

    private static List<Map<String, Object>> summarize(List<Requirement> requirements) {
        return requirements.stream().<Map<String, Object>>map(r -> Map.of(
                "ref", r.ref(), "title", r.title(), "text", r.text(), "type", r.type().name(),
                "numericField", r.numericField() == null ? "" : r.numericField().name(),
                "numericOperator", r.numericOperator() == null ? "" : r.numericOperator(),
                "numericValue", r.numericValue() == null ? "" : r.numericValue().toPlainString())).toList();
    }

    static String slug(String label) {
        String slug = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "rule-text" : slug;
    }

    Document ruleDocument(RuleVersion version) {
        return documents.findById(version.documentId()).orElseThrow();
    }
}
