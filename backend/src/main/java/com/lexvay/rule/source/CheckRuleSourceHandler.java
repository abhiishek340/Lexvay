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
 * Fetches one rule source and records what it found (F12, D-184). A changed text becomes a stored document and an
 * UNAPPROVED draft rule version made through {@link RuleVersionService}, so extraction and the change event follow
 * the path an upload takes; this class never approves, edits or replaces anything. The draft says when the text was
 * observed and nothing else: its legal status is UNKNOWN and it has no effective date, because a crawler seeing a text
 * says nothing about when the law takes effect (D-551). A change seen while a draft already waits is stored as well,
 * as a CHANGED_QUEUED check linked to that draft, so no observation is lost; the baseline stays the drafted text, so
 * once that draft is approved the next check drafts the page as it then reads (D-555). A page that cannot be fetched
 * or read is a recorded check, not a failed job: the source is checked again after a short, growing delay (D-381).
 */
@Component
public class CheckRuleSourceHandler implements JobHandler {

    private static final int MAX_DETAIL_CHARS = 1000;

    /** What a check found, before it is recorded. */
    private record Finding(CheckOutcome outcome, Integer httpStatus, String contentHash, String detail,
                           UUID documentId, UUID ruleVersionId) {

        static Finding failed(Integer httpStatus, String message) {
            String text = message == null || message.isBlank() ? "The page could not be read" : message;
            String detail = text.length() <= MAX_DETAIL_CHARS ? text : text.substring(0, MAX_DETAIL_CHARS);
            return new Finding(CheckOutcome.FETCH_FAILED, httpStatus, null, detail, null, null);
        }
    }

    private final RuleSourceRepository sources;
    private final RuleVersionRepository ruleVersions;
    private final RuleVersionService ruleVersionService;
    private final DocumentService documents;
    private final TextExtractor extractor;
    private final HttpFetcher fetcher;
    private final AuditService audit;

    public CheckRuleSourceHandler(RuleSourceRepository sources, RuleVersionRepository ruleVersions,
                                  RuleVersionService ruleVersionService, DocumentService documents, TextExtractor extractor,
                                  HttpFetcher fetcher, AuditService audit) {
        this.sources = sources;
        this.ruleVersions = ruleVersions;
        this.ruleVersionService = ruleVersionService;
        this.documents = documents;
        this.extractor = extractor;
        this.fetcher = fetcher;
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
        OffsetDateTime observedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Finding finding = check(source, observedAt);

        UUID checkId = sources.insertCheck(TenantContext.require(), sourceId, observedAt, finding.outcome(), finding.httpStatus(),
                finding.contentHash(), finding.detail(), finding.documentId(), finding.ruleVersionId());
        // Only a new draft moves the baseline; a change kept behind a waiting draft is drafted once that one is decided.
        sources.markChecked(sourceId, finding.outcome() == CheckOutcome.CHANGED ? finding.contentHash() : source.lastContentHash(),
                finding.outcome());
        audit.recordAs(null, "RULE_SOURCE_CHECKED", "rule_source", sourceId, auditDetail(job, checkId, finding));
    }

    private Finding check(RuleSource source, OffsetDateTime observedAt) {
        HttpFetcher.Fetched fetched;
        try {
            fetched = fetcher.fetch(URI.create(source.url()));
        } catch (FetchRejected e) {
            return Finding.failed(null, e.getMessage());
        }
        if (fetched.status() < 200 || fetched.status() >= 300) {
            return Finding.failed(fetched.status(), "The server answered HTTP " + fetched.status());
        }
        FileType type;
        List<ExtractedPage> pages;
        try {
            type = FileType.detectFetched(fetched.contentType(), fetched.body());
            pages = extractor.extract(type, fetched.body());
        } catch (IllegalArgumentException | UnparsableDocumentException | EmptyDocumentException | TooManyScannedPagesException e) {
            return Finding.failed(fetched.status(), e.getMessage());
        }
        String hash = ContentHash.of(pages);
        if (hash.equals(source.lastContentHash())) {
            return new Finding(CheckOutcome.UNCHANGED, fetched.status(), hash, null, null, null);
        }

        Optional<RuleVersion> current = ruleVersions.findCurrentApproved(source.jurisdictionId(), source.licenseType());
        UUID supersedesId = current.map(RuleVersion::id).orElse(null);
        Optional<RuleVersion> draft = ruleVersions.findDraft(source.jurisdictionId(), source.licenseType(), supersedesId);

        if (draft.isPresent() && sources.latestQueuedHash(source.id(), draft.get().id()).filter(hash::equals).isPresent()) {
            return new Finding(CheckOutcome.UNCHANGED, fetched.status(), hash,
                    "The text is the one already kept behind the waiting draft", null, null);
        }
        String label = source.label() + ", observed " + observedAt.toLocalDate();
        UUID documentId = store(fetched.body(), RuleVersionService.slug(label) + "." + type.extension(), pages);
        if (draft.isPresent()) {
            return new Finding(CheckOutcome.CHANGED_QUEUED, fetched.status(), hash,
                    "Kept behind draft rule version \"" + draft.get().label() + "\" (" + draft.get().id() + "), which is waiting for review",
                    documentId, draft.get().id());
        }
        UUID ruleVersionId = ruleVersionService.createFromDocument(new RuleVersionService.NewVersion(
                source.jurisdictionCode(), source.licenseType(), label, source.url(), LegalPosition.unknown(), observedAt,
                supersedesId), documentId).ruleVersionId();
        return new Finding(CheckOutcome.CHANGED, fetched.status(), hash,
                "Draft rule version \"" + label + "\" created; it is not approved", documentId, ruleVersionId);
    }

    /** Content this tenant already holds, for example a page that went back to an earlier text, is reused. */
    private UUID store(byte[] bytes, String filename, List<ExtractedPage> pages) {
        try {
            return documents.storeRuleText(bytes, filename, pages);
        } catch (DuplicateDocumentException existing) {
            return existing.existingDocumentId();
        }
    }

    private static Map<String, Object> auditDetail(Job job, UUID checkId, Finding finding) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("jobId", job.id().toString());
        detail.put("checkId", checkId.toString());
        detail.put("outcome", finding.outcome().name());
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
