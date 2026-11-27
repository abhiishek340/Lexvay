package com.lexvay.eval;

import com.lexvay.assessment.match.Passage;
import com.lexvay.assessment.match.PassageSearch;
import com.lexvay.assessment.match.PassageSearch.Ranker;
import com.lexvay.common.TenantContext;
import com.lexvay.document.DocumentKind;
import com.lexvay.seed.SeedLoader;
import com.lexvay.seed.SeedManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Measures how well each ranker finds the page that answers a requirement (ROADMAP F7). Loads the demo seed's PDFs and the
 * added syllabi of eval/retrieval into a fresh, uniquely named tenant through {@link SeedLoader}, then puts every query
 * to the production {@link PassageSearch} once per ranker, and BM25 once more with the query's search terms and scores the shortlists and their excerpts.
 */
@Service
public class RetrievalService {

    private final SeedLoader seeds;
    private final PassageSearch search;
    private final int shortlistSize;

    RetrievalService(SeedLoader seeds, PassageSearch search, @Value("${lexvay.matching.shortlist-size:8}") int shortlistSize) {
        this.seeds = seeds;
        this.search = search;
        this.shortlistSize = shortlistSize;
    }

    public RetrievalReport run(Path seedDir, Path retrievalDir, RetrievalQueries queries, Thresholds.Retrieval limits) {
        if (!queries.fictional()) {
            throw new IllegalStateException("eval/retrieval/queries.yml must declare fictional: true");
        }
        if (shortlistSize != RetrievalScorer.DEEP) {
            throw new IllegalStateException("lexvay.matching.shortlist-size is " + shortlistSize + " but recall is measured at " + RetrievalScorer.DEEP);
        }
        SeedManifest.Tenant demo = seeds.manifest(seedDir).tenants().stream().filter(SeedManifest.Tenant::demo).findFirst()
                .orElseThrow(() -> new IllegalStateException("seed/manifest.yml has no demo tenant"));
        Map<Path, DocumentKind> pdfs = corpus(seedDir.resolve("pdf"), demo);
        pdfs.putAll(corpus(retrievalDir.resolve("pdf"), demo));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SeedLoader.Corpus loaded = seeds.loadCorpus("Retrieval evaluation " + suffix, "retrieval." + suffix + "@evaluation.example",
                UUID.randomUUID().toString(), demo.program(), pdfs);
        return TenantContext.runAs(loaded.tenantId(), () -> RetrievalReport.of(loaded.documents(),
                results(loaded.programId(), queries, Ranker.TS_RANK, false), results(loaded.programId(), queries, Ranker.BM25, false),
                results(loaded.programId(), queries, Ranker.BM25, true), limits));
    }

    /** The shortlists of every query; the search terms of a query are put to the search only when {@code withTerms}. */
    private List<RetrievalScorer.Result> results(UUID programId, RetrievalQueries queries, Ranker ranker, boolean withTerms) {
        return queries.queries().stream().map(q -> new RetrievalScorer.Result(q, search
                .search(programId, q.title(), q.text(), withTerms ? q.searchTerms() : List.of(), shortlistSize, ranker).stream()
                .map(RetrievalService::page).toList())).toList();
    }

    private static RetrievalScorer.Page page(Passage p) {
        return new RetrievalScorer.Page(p.filename(), p.pageNumber(), p.excerpt());
    }

    /** Every PDF of a folder in file name order. The demo tenant's declared documents keep their kind; every other one is a syllabus. */
    private static Map<Path, DocumentKind> corpus(Path pdfDir, SeedManifest.Tenant demo) {
        Map<String, DocumentKind> declared = new HashMap<>();
        demo.documents().forEach(d -> declared.put(d.file(), DocumentKind.valueOf(d.kind())));
        Map<Path, DocumentKind> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(pdfDir)) {
            files.filter(p -> p.toString().endsWith(".pdf")).sorted()
                    .forEach(p -> out.put(p, declared.getOrDefault(p.getFileName().toString(), DocumentKind.SYLLABUS)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + pdfDir, e);
        }
        return out;
    }
}
