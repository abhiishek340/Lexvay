package com.lexvay.rule;

import com.lexvay.common.TextDiff;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What changed between two versions of a rule, requirement by requirement (F3a). Read-only. */
@Service
public class RuleVersionDiffService {

    public enum Status {
        ADDED, REMOVED, CHANGED, UNCHANGED
    }

    public record VersionRef(UUID id, String label, LocalDate effectiveFrom) {
    }

    public record Summary(long added, long removed, long changed, long unchanged) {
    }

    public record Row(Status status, boolean refChanged, Requirement before, Requirement after,
                      List<RequirementComparison.Aspect> changes, List<TextDiff.Segment> textDiff) {
    }

    public record Diff(VersionRef from, VersionRef to, Summary summary, List<Row> requirements,
                       List<TextDiff.Segment> ruleTextDiff) {
    }

    private final RuleVersionService ruleVersions;
    private final RuleVersionRepository repository;

    public RuleVersionDiffService(RuleVersionService ruleVersions, RuleVersionRepository repository) {
        this.ruleVersions = ruleVersions;
        this.repository = repository;
    }

    /** Diff from {@code against} (default: the version {@code id} supersedes) to {@code id}. */
    @Transactional(readOnly = true)
    public Diff diff(UUID id, UUID against) {
        RuleVersion to = ruleVersions.require(id);
        UUID fromId = against != null ? against : to.supersedesId();
        if (fromId == null) {
            throw new IllegalArgumentException("This rule version does not supersede another one; pass the version to compare against");
        }
        RuleVersion from = ruleVersions.require(fromId);
        if (!from.jurisdictionId().equals(to.jurisdictionId()) || !from.licenseType().equals(to.licenseType())) {
            throw new IllegalArgumentException("Only rule versions for the same jurisdiction and license type can be compared");
        }

        List<Row> rows = RequirementMatcher.match(repository.requirements(from.id()), repository.requirements(to.id())).stream()
                .map(RuleVersionDiffService::row).toList();
        Summary summary = new Summary(count(rows, Status.ADDED), count(rows, Status.REMOVED), count(rows, Status.CHANGED),
                count(rows, Status.UNCHANGED));
        return new Diff(ref(from), ref(to), summary, rows, TextDiff.lines(ruleVersions.ruleText(from), ruleVersions.ruleText(to)));
    }

    private static Row row(RequirementMatcher.Pair pair) {
        if (pair.before() == null) {
            return new Row(Status.ADDED, false, null, pair.after(), List.of(), null);
        }
        if (pair.after() == null) {
            return new Row(Status.REMOVED, false, pair.before(), null, List.of(), null);
        }
        List<RequirementComparison.Aspect> changes = List.copyOf(RequirementComparison.changes(pair.before(), pair.after()));
        if (changes.isEmpty() && !pair.refChanged()) {
            return new Row(Status.UNCHANGED, false, pair.before(), pair.after(), changes, null);
        }
        return new Row(Status.CHANGED, pair.refChanged(), pair.before(), pair.after(), changes,
                TextDiff.words(pair.before().text(), pair.after().text()));
    }

    private static long count(List<Row> rows, Status status) {
        return rows.stream().filter(r -> r.status() == status).count();
    }

    private static VersionRef ref(RuleVersion version) {
        return new VersionRef(version.id(), version.label(), version.effectiveFrom());
    }
}
