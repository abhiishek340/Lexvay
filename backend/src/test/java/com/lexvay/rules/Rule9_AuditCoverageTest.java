package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Product rule 9 guard: every mutating endpoint must be in this declared set, and each entry must have
 * an integration test asserting its audit row. Adding an endpoint without updating both fails the build.
 */
class Rule9_AuditCoverageTest extends AbstractDatabaseTest {

    /** Endpoint → test that asserts its audit row. */
    static final Set<String> AUDITED_MUTATING_ENDPOINTS = Set.of(
            "POST /api/v1/programs",                        // ProgramApiTest.createWritesAuditRow
            "POST /api/v1/programs/{programId}/courses",    // CourseApiTest.createWritesAuditRow
            "POST /api/v1/programs/{programId}/documents",  // DocumentUploadTest.pdfUploadIsIngestedWithOffsetsAndAudited
            "POST /api/v1/rule-versions",                   // RuleVersionApiTest.createStoresRuleTextAndExtractsRequirements
            "PUT /api/v1/rule-versions/{id}/requirements",  // RuleVersionApiTest.reviewerCanReplaceRequirementsBeforeApproval
            "POST /api/v1/rule-versions/{id}/requirements/approve", // RuleVersionApiTest.approvalIsAuditedAndHappensOnce
            "POST /api/v1/rule-versions/{id}/status",       // LegalTimeApiTest.aReviewerRecordsTheStatusThenApprovesAndTheApprovalPinsItAsAStatusRow
            "POST /api/v1/programs/{programId}/facts",             // ProgramFactApiTest.verifiedQuoteBecomesEvidenceAndIsAudited
            "POST /api/v1/programs/{programId}/assessments",       // AssessmentApiTest.fullRunProducesMappingsWithEvidence
            "POST /api/v1/mappings/{id}/reviews",                  // ReviewApiTest.decisionsAreRecordedAndTheLatestWins
            "POST /api/v1/mappings/{id}/evidence-responses",       // EvidenceRequestApiTest.fullLoopFromRequestToReviewerDecision
            "POST /api/v1/programs/{programId}/determinations",    // DeterminationApiTest.signerSignsWhenEveryMappingIsDecided
            "POST /api/v1/courses/{courseId}/versions",            // CourseVersionChangeTest.newCourseVersionCreatesEventMarksStaleAndReopens
            "POST /api/v1/change-events/{id}/rerun",               // RuleVersionChangeTest.rerunCarriesOverUnchangedAndReevaluatesChanged
            "POST /api/v1/determinations/{id}/report",             // ReportTest.signedDeterminationReportIsGeneratedAndDownloadable
            "POST /api/v1/reports/check",                          // ReportCheckTest.theDownloadedFileMatchesItsReportWithEveryFieldAndTheCheckIsAudited
            "POST /api/v1/rule-sources",                           // RuleSourceApiTest.createStoresTheSourceQueuesTheFirstCheckAndIsAudited
            "POST /api/v1/rule-sources/{id}/check",                // RuleSourceApiTest.aCheckCanBeRequestedOnceAtATimeAndIsAudited
            "PUT /api/v1/rule-sources/{id}/active",                // RuleSourceApiTest.onlyAnAdminTurnsMonitoringOffAndOnAndItIsAudited
            "POST /api/v1/users",                                 // UserAdminApiTest.creatingAUserIsAuditedWithoutTheCode
            "POST /api/v1/users/{id}/setup-code",                 // UserAdminApiTest.aNewCodeClosesTheEarlierOnesAndIsAudited
            "PUT /api/v1/users/{id}/role",                        // UserAdminApiTest.aRoleChangeIsAuditedWithTheOldAndNewRole
            "PUT /api/v1/users/{id}/active",                      // UserAdminApiTest.deactivationAndReactivationAreAudited
            "POST /api/v1/auth/setup"                             // PasswordSetupApiTest.aValidCodeSetsThePasswordOnceAndIsAuditedAsThePerson
    );

    /**
     * Reads that are audited all the same, because they hand signed records out of the system (D-274 in
     * docs/decisions/F8.md; the audit export, D-377). Endpoint → test that asserts its audit row.
     */
    static final Set<String> AUDITED_READ_ENDPOINTS = Set.of(
            "GET /api/v1/determinations/register.csv",             // RegisterApiTest.theCsvExportIsRfc4180WithLabelsAndIsAudited
            "GET /api/v1/audit.csv"                               // AuditExportApiTest.theExportIsAuditedWithItsFiltersAndRowCount
    );

    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyMutatingEndpointIsDeclaredAudited() {
        var mutating = new TreeSet<String>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            for (RequestMethod verb : info.getMethodsCondition().getMethods()) {
                if (verb == RequestMethod.GET || verb == RequestMethod.HEAD || verb == RequestMethod.OPTIONS) {
                    continue;
                }
                for (var pattern : info.getPathPatternsCondition().getPatterns()) {
                    mutating.add(verb + " " + pattern.getPatternString());
                }
            }
        });
        assertThat(mutating).containsExactlyInAnyOrderElementsOf(AUDITED_MUTATING_ENDPOINTS);
    }

    @Test
    void everyAuditedReadEndpointExists() {
        var reads = new TreeSet<String>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (info.getMethodsCondition().getMethods().contains(RequestMethod.GET)) {
                info.getPathPatternsCondition().getPatterns().forEach(p -> reads.add("GET " + p.getPatternString()));
            }
        });
        assertThat(reads).containsAll(AUDITED_READ_ENDPOINTS);
    }
}
