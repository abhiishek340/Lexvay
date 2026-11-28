package com.lexvay.overview;

import com.lexvay.assessment.AssessmentService.AssessmentView;
import com.lexvay.change.ChangeEventView;
import com.lexvay.determination.Determination;
import com.lexvay.program.Program;
import java.util.List;

/**
 * Everything the program screen needs in one response. {@code assessment} is the latest FINISHED one, or
 * null before the first run. {@code undecidedTotal} counts current mappings in it that still need a human
 * decision or cite an outdated version, across all jurisdictions. {@code rules} says, per target jurisdiction, which
 * rule is in force today or that none is.
 */
public record ProgramOverview(Program program, AssessmentView assessment, List<Determination> determinations,
                              List<ChangeEventView> openChangeEvents, long undecidedTotal, List<RuleStanding> rules) {
}
