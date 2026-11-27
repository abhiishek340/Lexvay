package com.lexvay.coverage;

import com.lexvay.assessment.Finding;
import com.lexvay.review.ReviewKind;
import com.lexvay.rule.RequirementType;
import java.util.List;
import java.util.UUID;

/**
 * Which course carries the evidence for which requirement of one jurisdiction, in the program's latest finished
 * assessment. It reports what the evidence shows and what people decided; it concludes nothing (product rule 1).
 * {@code assessmentId} and {@code ruleVersion} are null before the first finished assessment, or when it holds no
 * mapping for the jurisdiction.
 */
public record CoverageMatrix(ProgramRef program, JurisdictionRef jurisdiction, UUID assessmentId, RuleVersionRef ruleVersion,
                             List<Course> courses, List<Requirement> requirements) {

    public record ProgramRef(UUID id, String name) {
    }

    public record JurisdictionRef(String code, String name) {
    }

    public record RuleVersionRef(UUID id, String label) {
    }

    /** A current course version of the program: a column of the matrix. */
    public record Course(UUID courseId, UUID courseVersionId, String code, String title, String catalogYear, boolean required) {
    }

    /**
     * One row. {@code decision} is the latest review decision, null when nobody decided or the mapping was reopened.
     * {@code cells} are the current course versions whose documents the evidence cites; {@code outdatedCells} the
     * superseded course versions it cites (D-394); {@code programLevel} the cited documents that belong to no course.
     */
    public record Requirement(UUID mappingId, String ref, String title, RequirementType type, Finding finding, ReviewKind decision,
                              List<Cell> cells, List<OutdatedCell> outdatedCells, List<ProgramDocument> programLevel, Risk risk) {
    }

    /** Evidence in a document of a superseded course version; {@code supersededBy} is the course's current version, a column of {@code courses}. */
    public record OutdatedCell(UUID courseVersionId, String code, String catalogYear, UUID supersededBy, Source source, int quoteCount) {
    }

    /** {@code quoteCount} is the number of distinct verified quotes of that source in documents of the course version. */
    public record Cell(UUID courseVersionId, Source source, int quoteCount) {
    }

    public record ProgramDocument(UUID documentId, String filename, String kind, Source source) {
    }

    /** SYSTEM: the mapping's own verified evidence span. PERSON: a contributor's evidence response (F4a). */
    public enum Source {
        SYSTEM, PERSON
    }

    /** Descriptive flags with fixed names; they say where the evidence sits and never judge it (D-392). */
    public enum Risk {
        NONE, OUTDATED_EVIDENCE, NO_EVIDENCE, ELECTIVE_ONLY, SINGLE_COURSE
    }
}
