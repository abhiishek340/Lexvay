package com.lexvay.assessment.match;

import com.lexvay.assessment.EvidenceSpanRepository;
import com.lexvay.assessment.Finding;
import com.lexvay.common.TenantContext;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.llm.JsonSchemaValidator;
import com.lexvay.llm.LlmCallRecorder;
import com.lexvay.llm.LlmClient;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRequest;
import com.lexvay.llm.LlmResponse;
import com.lexvay.rule.Requirement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pipeline stage 4 for SUBJECT, ACCREDITATION and JURISDICTION_SPECIFIC requirements. Shortlists pages,
 * asks the model for one of three findings with a verbatim quote, then applies the code-side guardrails:
 * schema and finding check (rule 1), quote verification (rule 2), and "when unsure, ambiguous" (rule 4) through
 * the ordered {@link DowngradeRule}s: non-required course, thin quote, low OCR confidence. Every proposal's raw
 * input and output is stored.
 */
@Component
public class EvidenceMatcher {

    public static final String PROMPT_VERSION = "match-v1";
    public static final String SCHEMA = "llm/evidence-match.schema.json";
    public static final String NO_VERIFIABLE_PASSAGE = "No verifiable passage found";

    public record Outcome(Finding finding, String rationale, UUID evidenceSpanId) {
    }

    private final PassageSearch search;
    private final LlmClient llm;
    private final LlmCallRecorder calls;
    private final EvidenceSpanRepository spans;
    private final JsonSchemaValidator validator;
    private final ObjectMapper json;
    private final int shortlistSize;
    private final List<DowngradeRule> downgrades;
    private final int maxOutputTokens;
    private final String systemPrompt;

    public EvidenceMatcher(PassageSearch search, LlmClient llm, LlmCallRecorder calls, EvidenceSpanRepository spans,
                           ObjectMapper json,
                           @Value("${lexvay.matching.shortlist-size:8}") int shortlistSize,
                           @Value("${lexvay.matching.min-quote-chars:40}") int minQuoteChars,
                           @Value("${lexvay.matching.min-ocr-confidence:80}") double minOcrConfidence,
                           @Value("${lexvay.llm.max-output-tokens:16000}") int maxOutputTokens) {
        this.search = search;
        this.llm = llm;
        this.calls = calls;
        this.spans = spans;
        this.json = json;
        this.validator = new JsonSchemaValidator(json, SCHEMA);
        this.shortlistSize = shortlistSize;
        this.downgrades = DowngradeRule.ordered(minQuoteChars, minOcrConfidence);
        this.maxOutputTokens = maxOutputTokens;
        try {
            this.systemPrompt = new String(new ClassPathResource("llm/" + PROMPT_VERSION + ".system.txt").getInputStream()
                    .readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Outcome match(UUID assessmentId, UUID programId, Requirement requirement, UUID actor) {
        List<Passage> passages = search.search(programId, requirement.title() + " " + requirement.text(), shortlistSize);
        if (passages.isEmpty()) {
            return new Outcome(Finding.EVIDENCE_MISSING, "No candidate passages found in the program's documents", null);
        }
        LlmRequest request = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:" + requirement.ref(), systemPrompt,
                userPrompt(requirement, passages), validator.schema(), maxOutputTokens);
        LlmResponse response = llm.complete(request);
        calls.record(request, response, PROMPT_VERSION, requirement.ruleVersionId(), assessmentId, requirement.id());

        MatchProposal proposal;
        try {
            proposal = parse(response.text(), passages);
        } catch (IllegalArgumentException rejected) {
            return new Outcome(Finding.EVIDENCE_MISSING, "Model output rejected: " + rejected.getMessage(), null);
        }
        if (proposal.finding() == Finding.EVIDENCE_MISSING) {
            return new Outcome(Finding.EVIDENCE_MISSING, proposal.rationale(), null);
        }

        Passage passage = passages.stream()
                .filter(p -> p.documentId().equals(proposal.documentId()) && p.pageNumber() == proposal.pageNumber())
                .findFirst().orElseThrow();
        Optional<QuoteVerifier.Match> verified = QuoteVerifier.find(passage.text(), proposal.quote());
        if (verified.isEmpty()) {
            return new Outcome(Finding.EVIDENCE_MISSING, NO_VERIFIABLE_PASSAGE, null);
        }
        QuoteVerifier.Match match = verified.get();
        UUID spanId = spans.insert(TenantContext.require(), passage.documentId(), passage.pageNumber(), match.charStart(),
                match.charEnd(), match.exactText(), actor);

        Finding finding = proposal.finding();
        String rationale = proposal.rationale();
        if (finding == Finding.EVIDENCE_FOUND) {
            for (DowngradeRule rule : downgrades) {
                Optional<String> reason = rule.reason(passage, match);
                if (reason.isPresent()) {
                    finding = Finding.AMBIGUOUS;
                    rationale += " Downgraded to ambiguous: " + reason.get();
                }
            }
        }
        return new Outcome(finding, rationale, spanId);
    }

    /** Schema, allowed finding, and a document and page that were actually offered. Anything else is rejected. */
    MatchProposal parse(String text, List<Passage> passages) {
        JsonSchemaValidator.Result result = validator.validate(text);
        if (!result.valid()) {
            throw new IllegalArgumentException(String.join("; ", result.errors()));
        }
        JsonNode node = result.value();
        Finding finding;
        try {
            finding = Finding.valueOf(node.path("finding").asString(""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("finding '" + node.path("finding").asString("") + "' is not one of the three allowed values");
        }
        String rationale = node.path("rationale").asString("").trim();
        if (rationale.isEmpty()) {
            rationale = "No rationale given";
        }
        if (finding == Finding.EVIDENCE_MISSING) {
            return new MatchProposal(finding, null, null, null, rationale);
        }
        JsonNode documentNode = node.path("document_id");
        JsonNode pageNode = node.path("page_number");
        JsonNode quoteNode = node.path("quote");
        if (documentNode.isNull() || pageNode.isNull() || quoteNode.isNull() || quoteNode.asString("").isBlank()) {
            throw new IllegalArgumentException(finding + " requires document_id, page_number and a quote");
        }
        UUID documentId;
        try {
            documentId = UUID.fromString(documentNode.asString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("document_id is not a valid id");
        }
        int page = pageNode.asInt();
        boolean offered = passages.stream().anyMatch(p -> p.documentId().equals(documentId) && p.pageNumber() == page);
        if (!offered) {
            throw new IllegalArgumentException("document " + documentId + " page " + page + " was not among the candidate passages");
        }
        return new MatchProposal(finding, documentId, page, quoteNode.asString(), rationale);
    }

    static String userPrompt(Requirement requirement, List<Passage> passages) {
        StringBuilder sb = new StringBuilder();
        sb.append("REQUIREMENT ").append(requirement.ref()).append(" (").append(requirement.type()).append(")\n")
                .append(requirement.title()).append('\n').append(requirement.text()).append("\n\nCANDIDATE PASSAGES\n");
        int n = 1;
        for (Passage p : passages) {
            sb.append("\n--- Passage ").append(n++).append(" ---\n")
                    .append("document_id: ").append(p.documentId()).append('\n')
                    .append("page_number: ").append(p.pageNumber()).append('\n')
                    .append("document: ").append(p.filename()).append(" (").append(p.kind()).append(")\n");
            if (p.courseCode() != null) {
                sb.append("course: ").append(p.courseCode()).append(Boolean.TRUE.equals(p.courseRequired()) ? " (required)" : " (elective)").append('\n');
            }
            sb.append("text:\n").append(p.text()).append('\n');
        }
        return sb.toString();
    }
}
