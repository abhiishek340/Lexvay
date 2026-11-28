package com.lexvay.evidencepackage;

import com.lexvay.assessment.Mapping;
import com.lexvay.common.CsvWriter;
import com.lexvay.evidencepackage.PackageSourceRepository.Quote;
import com.lexvay.rule.LegalPosition;
import com.lexvay.rule.RuleVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The text entries of a package, as the rows and records a reader of the ZIP sees. Every format is fixed here. */
final class PackageEntries {

    static final List<String> REQUIREMENT_COLUMNS = List.of("Requirement", "Title", "Kind", "Check", "Finding", "Decision",
            "Decided by", "Decided at (UTC)", "Note");
    static final List<String> QUOTE_COLUMNS = List.of("Requirement", "Document", "Page", "Char start", "Char end", "Quote",
            "Page SHA-256");

    private static final String DOCUMENTS = "documents/";

    private PackageEntries() {
    }

    /** A document a quote cites, with its place in the ZIP and the hash its stored bytes must have. */
    record CitedDocument(UUID id, String path, String storageKey, String sha256) {
    }

    record DeterminationJson(UUID id, ProgramJson program, StateJson state, String outcome, String outcomeLabel, SignerJson signer,
                             Instant signedAt, String status, String versionsHash, RuleVersionJson ruleVersion,
                             List<CourseVersionJson> courseVersions) {
    }

    record ProgramJson(UUID id, String name, String licenseType, String catalogYear) {
    }

    record StateJson(String code, String name) {
    }

    record SignerJson(UUID id, String name) {
    }

    /** The rule version with its current legal status and dates and where the dates come from (null when not recorded). */
    record RuleVersionJson(UUID id, String label, String legalStatus, String effectiveFrom, String effectiveTo, String datesBasis,
                           String sourceUrl) {
    }

    record CourseVersionJson(UUID id, String code, String title, String catalogYear) {
    }

    record ManifestJson(UUID packageId, UUID determinationId, Instant generatedAt, String lexvayVersion, String hashAlgorithm,
                        List<PackageZip.Entry> entries) {
    }

    static DeterminationJson determination(PackageSnapshot s) {
        var d = s.determination();
        return new DeterminationJson(d.id(),
                new ProgramJson(s.program().id(), s.program().name(), s.program().licenseType(), s.program().catalogYear()),
                new StateJson(s.state().code(), s.state().name()), d.outcome().name(), d.outcome().label(),
                new SignerJson(d.signedBy(), d.signedByName()), d.signedAt().toInstant(), d.status().name(), d.versionsHash(),
                ruleVersion(s.ruleVersion()),
                s.courseVersions().stream().map(c -> new CourseVersionJson(c.id(), c.code(), c.title(), c.catalogYear())).toList());
    }

    private static RuleVersionJson ruleVersion(RuleVersion v) {
        LegalPosition legal = v.legal();
        return new RuleVersionJson(v.id(), v.label(), legal.status().name(), legal.effectiveFrom() == null ? null : legal.effectiveFrom().toString(),
                legal.effectiveTo() == null ? null : legal.effectiveTo().toString(), legal.datesBasis(), v.sourceUrl());
    }

    /** One row per requirement: the proposed finding and the person's final decision, or empty fields when there is none. */
    static void requirements(CsvWriter csv, List<Mapping> mappings) {
        csv.row(REQUIREMENT_COLUMNS);
        for (Mapping m : mappings) {
            Mapping.Decision decision = m.decision();
            csv.row(Arrays.asList(m.requirement().ref(), m.requirement().title(), m.requirement().type().name(), m.method().name(),
                    m.finding().name(), decision == null ? null : decision.kind(), decision == null ? null : decision.userDisplayName(),
                    decision == null ? null : decision.at().toInstant().toString(), decision == null ? null : decision.note()));
        }
    }

    static void quotes(CsvWriter csv, List<Quote> quotes, List<CitedDocument> documents) {
        Map<UUID, String> names = new HashMap<>();
        documents.forEach(d -> names.put(d.id(), d.path().substring(DOCUMENTS.length())));
        csv.row(QUOTE_COLUMNS);
        for (Quote q : quotes) {
            csv.row(List.of(q.requirementRef(), names.get(q.documentId()), String.valueOf(q.pageNumber()),
                    String.valueOf(q.charStart()), String.valueOf(q.charEnd()), q.quote(), q.pageSha256()));
        }
    }

    /**
     * The documents the quotes cite, once each, in filename order, with where each goes in the ZIP:
     * {@code documents/<filename>}. Two different documents with one filename (the same name in two courses) keep the
     * first as it is and prefix the others with the start of their id, so no entry is overwritten and the same inputs
     * always give the same paths.
     */
    static List<CitedDocument> citedDocuments(List<Quote> quotes) {
        Map<UUID, Quote> cited = new LinkedHashMap<>();
        quotes.stream().sorted(Comparator.comparing(Quote::filename).thenComparing(Quote::documentId))
                .forEach(q -> cited.putIfAbsent(q.documentId(), q));
        List<CitedDocument> documents = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (Quote q : cited.values()) {
            String name = q.filename().replaceAll("[\\\\/\\p{Cntrl}]", "_");
            if (!taken.add(name)) {
                name = q.documentId().toString().substring(0, 8) + "-" + name;
                taken.add(name);
            }
            documents.add(new CitedDocument(q.documentId(), DOCUMENTS + name, q.storageKey(), q.documentSha256()));
        }
        return documents;
    }
}
