package com.lexvay.coverage;

import com.lexvay.pathway.PathwayCoverage;
import com.lexvay.pathway.Tri;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The risk flag of a row whose evidence sits only in current courses (F10a, AC-1): whether the cited courses lie on every
 * valid graduation path. If a path avoids all of them the evidence rests on an elective; if the pathway does not say, that
 * is flagged too. Otherwise the row rests on one course or on several.
 */
final class PathwayRisk {

    private PathwayRisk() {
    }

    static CoverageMatrix.Risk of(CoverageMatrix.Requirement requirement, Map<UUID, PathwayCoverage.Course> courseOfVersion,
                                  PathwayCoverage pathway) {
        List<PathwayCoverage.Course> cited = requirement.cells().stream().map(CoverageMatrix.Cell::courseVersionId).distinct()
                .map(courseOfVersion::get).toList();
        Tri onEveryPath = pathway.onEveryPath(cited).value();
        return switch (onEveryPath) {
            case FALSE -> CoverageMatrix.Risk.ELECTIVE_ONLY;
            case UNKNOWN -> CoverageMatrix.Risk.PATHS_UNKNOWN;
            case TRUE -> cited.size() == 1 ? CoverageMatrix.Risk.SINGLE_COURSE : CoverageMatrix.Risk.NONE;
        };
    }
}
