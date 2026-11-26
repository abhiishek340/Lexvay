package com.lexvay.search;

import com.lexvay.assessment.match.PassageSearch;
import com.lexvay.document.DocumentKind;
import com.lexvay.program.ProgramService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets a reviewer search a program's own documents. Read-only: it never touches a mapping or a finding. */
@Service
public class SearchService {

    private final ProgramService programs;
    private final PassageSearch passages;

    public SearchService(ProgramService programs, PassageSearch passages) {
        this.programs = programs;
        this.passages = passages;
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(UUID programId, String query, DocumentKind kind, UUID courseVersionId, int limit) {
        if (kind == DocumentKind.RULE_TEXT) {
            throw new IllegalArgumentException("Rule text is not part of a program's documents and cannot be searched here");
        }
        programs.get(programId);
        return passages.searchWithHeadlines(programId, query.trim(), kind == null ? null : kind.name(), courseVersionId, limit)
                .stream()
                .map(h -> new SearchHit(h.documentId(), h.filename(), h.kind(), h.courseCode(), h.courseRequired(),
                        h.pageNumber(), h.rank(), HeadlineSegments.parse(h.headline())))
                .toList();
    }
}
