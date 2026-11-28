package com.lexvay.evidencepackage;

import com.lexvay.assessment.Mapping;
import com.lexvay.determination.Determination;
import com.lexvay.evidencepackage.PackageSourceRepository.LockedCourseVersion;
import com.lexvay.evidencepackage.PackageSourceRepository.Quote;
import com.lexvay.program.Program;
import com.lexvay.report.Report;
import com.lexvay.rule.Jurisdiction;
import com.lexvay.rule.RuleVersion;
import java.util.List;

/** Everything the database holds about one signed determination that goes into its package, read in one place. */
record PackageSnapshot(Determination determination, Program program, Jurisdiction state, RuleVersion ruleVersion,
                       List<LockedCourseVersion> courseVersions, List<Mapping> mappings, List<Quote> quotes, Report report) {
}
