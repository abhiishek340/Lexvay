package com.lexvay.evidencepackage;

import com.lexvay.assessment.MappingRepository;
import com.lexvay.determination.Determination;
import com.lexvay.job.PermanentJobFailure;
import com.lexvay.program.ProgramRepository;
import com.lexvay.report.ReportRepository;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.RuleVersionRepository;
import com.lexvay.tenant.TenantRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class PackageSnapshotLoader {

    private final ProgramRepository programs;
    private final JurisdictionRepository jurisdictions;
    private final RuleVersionRepository ruleVersions;
    private final MappingRepository mappings;
    private final PackageSourceRepository source;
    private final ReportRepository reports;
    private final TenantRepository tenants;

    PackageSnapshotLoader(ProgramRepository programs, JurisdictionRepository jurisdictions, RuleVersionRepository ruleVersions,
                          MappingRepository mappings, PackageSourceRepository source, ReportRepository reports,
                          TenantRepository tenants) {
        this.programs = programs;
        this.jurisdictions = jurisdictions;
        this.ruleVersions = ruleVersions;
        this.mappings = mappings;
        this.source = source;
        this.reports = reports;
        this.tenants = tenants;
    }

    /** Reads the records of {@code determination}; a record that is missing fails the job for good, retrying cannot restore it. */
    PackageSnapshot load(Determination determination) {
        return new PackageSnapshot(determination,
                programs.findById(determination.programId()).orElseThrow(() -> missing("program", determination)),
                jurisdictions.findByCodes(List.of(determination.jurisdiction())).get(0),
                ruleVersions.findById(determination.ruleVersionId()).orElseThrow(() -> missing("rule version", determination)),
                source.courseVersions(determination.id()),
                mappings.findByAssessmentAndJurisdiction(determination.assessmentId(), determination.jurisdiction()),
                source.quotes(determination.assessmentId(), determination.jurisdictionId()),
                reports.findLatestReady(determination.id()).orElseThrow(() -> missing("ready report", determination)),
                tenants.currentDataMode());
    }

    private static PermanentJobFailure missing(String what, Determination determination) {
        return new PermanentJobFailure("The " + what + " of determination " + determination.id() + " was not found");
    }
}
