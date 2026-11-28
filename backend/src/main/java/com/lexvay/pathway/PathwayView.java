package com.lexvay.pathway;

import com.lexvay.program.CreditUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A program's graduation paths for one catalog year, as the evaluator reads them. {@code minimumCredits} is the fewest
 * credits a graduate takes, null while {@code unknowns} says what is not recorded. It describes what people recorded and
 * concludes nothing about any licence (product rule 1).
 */
public record PathwayView(UUID programId, String catalogYear, List<Group> groups, BigDecimal minimumCredits,
                          CreditUnit minimumCreditsUnit, List<String> unknowns) {

    public record Group(UUID id, String label, GroupKind kind, Integer minCount, BigDecimal minCredits,
                        PathwayGroup.Citation citation, List<Member> members) {
    }

    /** {@code onEveryPath} and {@code onSomePath}: whether the course alone is on every valid path, and on some valid path. */
    public record Member(UUID courseId, String code, String title, BigDecimal credits, CreditUnit creditUnit,
                         Tri onEveryPath, Tri onSomePath) {
    }
}
