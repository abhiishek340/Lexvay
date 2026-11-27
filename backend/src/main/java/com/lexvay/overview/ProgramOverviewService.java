package com.lexvay.overview;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.assessment.AssessmentService.AssessmentView;
import com.lexvay.change.ChangeEventRepository;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.program.Program;
import com.lexvay.program.ProgramService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Composes the program screen's data inside one read-only transaction, so it is one connection and one snapshot. */
@Service
public class ProgramOverviewService {

    private final ProgramService programs;
    private final AssessmentService assessments;
    private final DeterminationRepository determinations;
    private final ChangeEventRepository changes;

    public ProgramOverviewService(ProgramService programs, AssessmentService assessments,
                                  DeterminationRepository determinations, ChangeEventRepository changes) {
        this.programs = programs;
        this.assessments = assessments;
        this.determinations = determinations;
        this.changes = changes;
    }

    @Transactional(readOnly = true)
    public ProgramOverview overview(UUID programId) {
        Program program = programs.get(programId);
        AssessmentView assessment = assessments.latestFinishedView(programId).orElse(null);
        long undecided = assessment == null ? 0 : assessment.jurisdictions().stream()
                .mapToLong(j -> j.total() - j.decided()).sum();
        return new ProgramOverview(program, assessment, determinations.findByProgram(programId),
                changes.findOpenViewsForProgram(programId), undecided);
    }
}
