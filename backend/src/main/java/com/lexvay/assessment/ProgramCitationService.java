package com.lexvay.assessment;

import com.lexvay.common.TenantContext;
import com.lexvay.document.Document;
import com.lexvay.document.DocumentKind;
import com.lexvay.document.DocumentPage;
import com.lexvay.document.DocumentRepository;
import com.lexvay.document.QuoteVerifier;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Turns a quote typed by a person into a stored evidence span, through the same verifier the model's quotes
 * pass (product rule 2). Used wherever a human cites a passage of a program document: program facts and
 * evidence responses. Call it inside the transaction that records what the citation supports.
 */
@Service
public class ProgramCitationService {

    /** The stored span, with the document and page it points into. */
    public record Citation(UUID spanId, Document document, DocumentPage page, QuoteVerifier.Match match) {
    }

    private final DocumentRepository documents;
    private final EvidenceSpanRepository spans;

    public ProgramCitationService(DocumentRepository documents, EvidenceSpanRepository spans) {
        this.documents = documents;
        this.spans = spans;
    }

    /** @throws IllegalArgumentException (HTTP 400) when the document, page or quote cannot be accepted */
    public Citation cite(UUID programId, UUID documentId, int pageNumber, String quote, UUID actor) {
        Document document = documents.findById(documentId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (document.kind() == DocumentKind.RULE_TEXT) {
            throw new IllegalArgumentException("Rule text is not program evidence");
        }
        if (!programId.equals(document.programId())) {
            throw new IllegalArgumentException("Document belongs to a different program");
        }
        DocumentPage page = documents.page(document.id(), pageNumber)
                .orElseThrow(() -> new IllegalArgumentException("Page " + pageNumber + " does not exist in this document"));
        QuoteVerifier.Match match = QuoteVerifier.find(page.text(), quote)
                .orElseThrow(() -> new IllegalArgumentException("The quote was not found on page " + pageNumber
                        + " of " + document.filename()));
        UUID spanId = spans.insert(TenantContext.require(), document.id(), page.pageNumber(), match.charStart(), match.charEnd(),
                match.exactText(), actor);
        return new Citation(spanId, document, page, match);
    }
}
