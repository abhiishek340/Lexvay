package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.review.ReviewFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Product rule 1: only a user with the signer role can sign a determination. Not reviewers, not admins. */
class Rule1_OnlySignerSignsTest extends AbstractAssessmentTest {

    @Test
    void everyOtherRoleIsForbiddenEvenWhenReviewIsComplete() throws Exception {
        var rf = new ReviewFixtures(mvc, json, fx);
        var s = rf.californiaScenario(t, "signer-only");
        rf.decideAll(t, s);
        for (var who : List.of(t.reviewer(), t.admin(), t.contributor())) {
            assertThat(rf.sign(who, s.programId(), "CA", "MEETS")).as(who.role().name()).hasStatus(403);
        }
        assertThat(rf.sign(t.signer(), s.programId(), "CA", "NOT_DETERMINED")).hasStatus(201);
    }
}
