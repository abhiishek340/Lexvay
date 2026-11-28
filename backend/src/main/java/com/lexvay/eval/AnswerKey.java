package com.lexvay.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lexvay.assessment.Finding;
import com.lexvay.rule.RequirementType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shape of eval/keys/&lt;key-id&gt;.yml: what qualified people, labelling on their own, say each requirement of one program's
 * approved rule versions finds in that program's documents. The file is validated against eval/keys/schema.json first
 * ({@link AnswerKeys}); {@link #problems()} checks what a schema cannot say, such as a span that names no document of the bundle.
 *
 * @param split the key is either tuned against ({@code dev}) or frozen and read only by a deliberate holdout run ({@code holdout})
 */
public record AnswerKey(Split split, Provenance provenance, List<Requirement> requirements) implements RequirementList {

    public enum Split {
        dev, holdout
    }

    public enum Severity {
        CRITICAL, STANDARD
    }

    /** The finding a labeller may give; unlike the system, a person may call a requirement not applicable to the program. */
    public enum Expected {
        EVIDENCE_FOUND(Finding.EVIDENCE_FOUND), EVIDENCE_MISSING(Finding.EVIDENCE_MISSING), AMBIGUOUS(Finding.AMBIGUOUS),
        NOT_APPLICABLE(null);

        private final Finding finding;

        Expected(Finding finding) {
            this.finding = finding;
        }

        /** The system's finding this label stands for; null for NOT_APPLICABLE, which the system cannot say. */
        public Finding finding() {
            return finding;
        }

        /** True when a person decided the requirement is covered or not covered, which is what an abstention is measured against. */
        public boolean decided() {
            return this == EVIDENCE_FOUND || this == EVIDENCE_MISSING;
        }
    }

    /** Who labelled, when, and from what. No names: a role says what qualified the labeller. */
    public record Provenance(boolean fictional, LocalDate date, List<Labeller> labellers, SourceBundle sourceBundle) {
    }

    public record Labeller(String id, String role, boolean independent) {
    }

    /** What the labellers read: the program, the approved rule versions and the documents, by content hash. */
    public record SourceBundle(Program program, List<RuleVersion> ruleVersions, List<BundleDocument> documents) {
    }

    public record Program(String name, String catalogYear) {
    }

    public record RuleVersion(String jurisdiction, String label, LocalDate effectiveFrom) {
    }

    public record BundleDocument(String file, String sha256) {
    }

    /** A passage a labeller accepts as evidence: a character range of one page of one document. */
    public record Span(String document, int page, int charStart, int charEnd) {

        /** Two spans match when the shared characters are at least this share of the shorter span. */
        static final double MINIMUM_OVERLAP = 0.5;

        public boolean overlaps(String otherDocument, int otherPage, int otherStart, int otherEnd) {
            if (!document.equals(otherDocument) || page != otherPage) {
                return false;
            }
            int shared = Math.min(charEnd, otherEnd) - Math.max(charStart, otherStart);
            int shorter = Math.min(charEnd - charStart, otherEnd - otherStart);
            return shared > 0 && shorter > 0 && (double) shared / shorter >= MINIMUM_OVERLAP;
        }
    }

    /** Recorded when two labellers disagreed: what each said and the final label, which is the one the requirement carries. */
    public record Adjudication(List<Label> labels, @JsonProperty("final") Expected finalLabel, String adjudicator) {
    }

    public record Label(String labeller, Expected finding) {
    }

    public record Requirement(String ref, String jurisdiction, String title, String text, RequirementType type,
                              Golden.Numeric numeric, List<String> searchTerms, Expected expected, Severity severity,
                              List<Span> spans, String notes, Adjudication adjudication) implements RequirementList.Item {

        public Requirement {
            searchTerms = searchTerms == null ? List.of() : searchTerms;
            spans = spans == null ? List.of() : spans;
        }

        public String key() {
            return Golden.key(jurisdiction, ref);
        }
    }

    @Override
    public JsonNode requirementList(String jurisdiction, ObjectMapper json) {
        return RequirementList.of(requirements, jurisdiction, json);
    }

    /** What is wrong with the key beyond its shape; empty means the key is internally consistent. */
    public List<String> problems() {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> documents = new HashSet<>();
        provenance.sourceBundle().documents().forEach(d -> documents.add(d.sha256()));
        Set<String> labellers = new HashSet<>();
        provenance.labellers().forEach(l -> labellers.add(l.id()));
        for (Requirement r : requirements) {
            String where = r.key();
            if (!seen.add(where)) {
                out.add(where + " appears twice");
            }
            if (r.type().isNumeric() != (r.numeric() != null)) {
                out.add(where + (r.type().isNumeric() ? " is numeric but has no numeric rule" : " is not numeric but has a numeric rule"));
            }
            boolean mustHaveSpan = r.expected() == Expected.EVIDENCE_FOUND;
            boolean mustHaveNone = r.expected() == Expected.EVIDENCE_MISSING || r.expected() == Expected.NOT_APPLICABLE;
            if (mustHaveSpan && r.spans().isEmpty()) {
                out.add(where + " is EVIDENCE_FOUND but accepts no evidence span");
            }
            if (mustHaveNone && !r.spans().isEmpty()) {
                out.add(where + " is " + r.expected() + " but accepts an evidence span");
            }
            for (Span span : r.spans()) {
                if (!documents.contains(span.document())) {
                    out.add(where + " has a span in a document that is not in the source bundle: " + span.document());
                }
                if (span.page() < 1 || span.charStart() < 0 || span.charEnd() <= span.charStart()) {
                    out.add(where + " has a span with an impossible range");
                }
            }
            if (r.adjudication() != null) {
                out.addAll(adjudicationProblems(where, r, labellers));
            }
        }
        return out;
    }

    private static List<String> adjudicationProblems(String where, Requirement r, Set<String> labellers) {
        List<String> out = new ArrayList<>();
        Adjudication a = r.adjudication();
        if (a.finalLabel() != r.expected()) {
            out.add(where + " is adjudicated to " + a.finalLabel() + " but carries " + r.expected());
        }
        if (a.labels().stream().map(Label::finding).distinct().count() < 2) {
            out.add(where + " is adjudicated but the labellers did not disagree");
        }
        a.labels().stream().map(Label::labeller).filter(id -> !labellers.contains(id))
                .forEach(id -> out.add(where + " names labeller " + id + ", who is not in the provenance"));
        return out;
    }
}
