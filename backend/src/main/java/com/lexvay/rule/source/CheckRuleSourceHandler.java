package com.lexvay.rule.source;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentService;
import com.lexvay.document.DuplicateDocumentException;
import com.lexvay.document.EmptyDocumentException;
import com.lexvay.document.ExtractedPage;
import com.lexvay.document.FileType;
import com.lexvay.document.TextExtractor;
import com.lexvay.document.TooManyScannedPagesException;
import com.lexvay.document.UnparsableDocumentException;
import com.lexvay.job.Job;
import com.lexvay.job.JobDeferred;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import com.lexvay.rule.LegalPosition;
import com.lexvay.rule.RuleVersion;
import com.lexvay.rule.RuleVersionRepository;
import com.lexvay.rule.RuleVersionService;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fetches one rule source and records what it found (F12, D-184). The fetch is polite (RG-2): the host's robots.txt and
 * request interval are asked first ({@link HostAdmission}; a source that must wait defers its job), and the request is
 * conditional when the source has validators. What comes back is validated before anything is made of it
 * ({@link ContentValidator}); a text that is not the rule text is recorded as SUSPECT_CONTENT and moves nothing.
 * A changed text becomes a stored document and an UNAPPROVED draft rule version made through
 * {@link RuleVersionService}, so extraction and the change event follow the path an upload takes; this class never
 * approves, edits or replaces anything. The draft says when the text was observed and nothing else: its legal status
 * is UNKNOWN and it has no effective date, because a crawler seeing a text says nothing about when the law takes
 * effect (D-551). A change seen while a draft already waits is stored as well, as a CHANGED_QUEUED check linked to that
 * draft, so no observation is lost; the baseline stays the drafted text, so once that draft is approved the next check
 * drafts the page as it then reads (D-555). A page that cannot be fetched or read is a recorded check, not a failed
 * job: the source is checked again after a short, growing delay (D-381). A source whose access is RESTRICTED or
 * BLOCKED is never fetched, whoever asks (D-568).
 */
@Component
public class CheckRuleSourceHandler implements JobHandler {

    private final RuleSourceRepository sources;
    private final RuleVersionRepository ruleVersions;
    private final RuleVersionService ruleVersionService;
    private final DocumentService documents;
    private final TextExtractor extractor;
    private final HttpFetcher fetcher;
    private final HostAdmission admission;
    private final AuditService audit;

    public CheckRuleSourceHandler(RuleSourceRepository sources, RuleVersionRepository ruleVersions,
                                  RuleVersionService ruleVersionService, DocumentService documents, TextExtractor extractor,
                                  HttpFetcher fetcher, HostAdmission admission, AuditService audit) {
        this.sources = sources;
        this.ruleVersions = ruleVersions;
        this.ruleVersionService = ruleVersionService;
        this.documents = documents;
        this.extractor = extractor;
        this.fetcher = fetcher;
        this.admission = admission;
        this.audit = audit;
    }

    @Override
    public JobType type() {
        return JobType.CHECK_RULE_SOURCE;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID sourceId = UUID.fromString(String.valueOf(payload.get("ruleSourceId")));
        RuleSource source = sources.findById(sourceId)
                .orElseThrow(() -> new PermanentJobFailure("Rule source " + sourceId + " not found"));
        AccessStatus access = source.governance().accessStatus();
        if (access.neverFetched()) {
            throw new PermanentJobFailure("Rule source " + sourceId + " has access status " + access + " and is never fetched");
        }
        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
        CheckFinding finding = check(source, observedAt);

        UUID checkId = sources.insertCheck(TenantContext.require(), sourceId, observedAt, access, finding);
        // Only a new draft moves the baseline; a change kept behind a waiting draft is drafted once that one is decided.
        String baseline = finding.outcome() == CheckOutcome.CHANGED ? finding.contentHash() : source.lastContentHash();
        sources.markChecked(sourceId, baseline, finding.validators() != null ? finding.validators() : source.validators(),
                finding.outcome());
        audit.recordAs(null, "RULE_SOURCE_CHECKED", "rule_source", sourceId, auditDetail(job, checkId, access, finding));
    }

    private CheckFinding check(RuleSource source, OffsetDateTime observedAt) {
        URI url = URI.create(source.url());
        switch (admission.admit(url)) {
            case HostAdmission.Wait wait -> throw new JobDeferred(wait.duration(), wait.reason());
            case HostAdmission.Disallowed disallowed -> {
                return CheckFinding.blockedByRobots(disallowed.reason());
            }
            case HostAdmission.Unavailable unavailable -> {
                return CheckFinding.failed(null, unavailable.reason());
            }
            case HostAdmission.Admitted admitted -> {
            }
        }
        HttpFetcher.Fetched fetched;
        try {
            fetched = fetcher.fetch(new HttpFetcher.FetchRequest(url, source.governance().scope(), source.validators()));
        } catch (FetchRejected e) {
            return CheckFinding.failed(null, e.getMessage());
        }
        return read(source, fetched, observedAt);
    }

    /** What the server answered, from its status to the change it means, if any. */
    private CheckFinding read(RuleSource source, HttpFetcher.Fetched fetched, OffsetDateTime observedAt) {
        int status = fetched.status();
        if (status == 304 && source.lastContentHash() != null) {
            return CheckFinding.notModified(status);
        }
        if (status < 200 || status >= 300) {
            if ((status == 429 || status == 503) && fetched.retryAfter() != null) {
                admission.hold(URI.create(source.url()), fetched.retryAfter());
            }
            return CheckFinding.failed(status, "The server answered HTTP " + status);
        }
        FileType type;
        try {
            type = FileType.detectFetched(fetched.contentType(), fetched.body());
        } catch (IllegalArgumentException e) {
            return CheckFinding.suspect(status, e.getMessage());
        }
        List<ExtractedPage> pages;
        try {
            pages = extractor.extract(type, fetched.body());
        } catch (IllegalArgumentException | UnparsableDocumentException | EmptyDocumentException | TooManyScannedPagesException e) {
            return CheckFinding.failed(status, e.getMessage());
        }
        String text = ContentHash.text(pages);
        Optional<String> suspicion = ContentValidator.suspicion(source, type, fetched.body(), text);
        if (suspicion.isPresent()) {
            return CheckFinding.suspect(status, suspicion.get());
        }
        return compare(source, fetched, type, pages, ContentHash.of(text), observedAt);
    }

    /** The text is valid: it is the baseline, or a change that is drafted or kept behind the draft that waits. */
    private CheckFinding compare(RuleSource source, HttpFetcher.Fetched fetched, FileType type, List<ExtractedPage> pages,
                                 String hash, OffsetDateTime observedAt) {
        int status = fetched.status();
        if (hash.equals(source.lastContentHash())) {
            return new CheckFinding(CheckOutcome.UNCHANGED, status, hash, null, null, null, fetched.validators());
        }

        Optional<RuleVersion> current = ruleVersions.findCurrentApproved(source.jurisdictionId(), source.licenseType());
        UUID supersedesId = current.map(RuleVersion::id).orElse(null);
        Optional<RuleVersion> draft = ruleVersions.findDraft(source.jurisdictionId(), source.licenseType(), supersedesId);

        if (draft.isPresent() && sources.latestQueuedHash(source.id(), draft.get().id()).filter(hash::equals).isPresent()) {
            return new CheckFinding(CheckOutcome.UNCHANGED, status, hash,
                    "The text is the one already kept behind the waiting draft", null, null, null);
        }
        String label = source.label() + ", observed " + observedAt.toLocalDate();
        UUID documentId = store(fetched.body(), RuleVersionService.slug(label) + "." + type.extension(), pages);
        if (draft.isPresent()) {
            return new CheckFinding(CheckOutcome.CHANGED_QUEUED, status, hash,
                    "Kept behind draft rule version \"" + draft.get().label() + "\" (" + draft.get().id() + "), which is waiting for review",
                    documentId, draft.get().id(), null);
        }
        UUID ruleVersionId = ruleVersionService.createFromDocument(new RuleVersionService.NewVersion(
                source.jurisdictionCode(), source.licenseType(), label, source.url(), LegalPosition.unknown(), observedAt,
                supersedesId), documentId).ruleVersionId();
        return new CheckFinding(CheckOutcome.CHANGED, status, hash,
                "Draft rule version \"" + label + "\" created; it is not approved", documentId, ruleVersionId, fetched.validators());
    }

    /** Content this tenant already holds, for example a page that went back to an earlier text, is reused. */
    private UUID store(byte[] bytes, String filename, List<ExtractedPage> pages) {
        try {
            return documents.storeRuleText(bytes, filename, pages);
        } catch (DuplicateDocumentException existing) {
            return existing.existingDocumentId();
        }
    }

    private static Map<String, Object> auditDetail(Job job, UUID checkId, AccessStatus access, CheckFinding finding) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("jobId", job.id().toString());
        detail.put("checkId", checkId.toString());
        detail.put("outcome", finding.outcome().name());
        detail.put("accessStatus", access.name());
        if (finding.httpStatus() != null) {
            detail.put("httpStatus", finding.httpStatus());
        }
        if (finding.documentId() != null) {
            detail.put("documentId", finding.documentId().toString());
            detail.put("ruleVersionId", finding.ruleVersionId().toString());
        }
        return detail;
    }
}
