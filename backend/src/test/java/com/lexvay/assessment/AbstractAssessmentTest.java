package com.lexvay.assessment;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

public abstract class AbstractAssessmentTest extends AbstractDatabaseTest {

    public static final String SYLLABUS_605 = """
            Fictional. Prototype data. COUN 605 Professional Orientation and Ethical Practice. 3 credits.
            This required course covers the ACA Code of Ethics, legal standards, confidentiality, duty to warn,
            and professional identity of clinical mental health counselors.
            Week 3: Legal standards, confidentiality and duty to warn in counseling practice.
            """;

    public static final String SYLLABUS_650 = """
            Fictional. Prototype data. COUN 650 Addictions Counseling (elective). 3 credits.
            This elective course covers the etiology of substance use disorders, screening and assessment of addictions,
            and evidence-based treatment of addictive behaviors across the lifespan.
            """;

    public static final String DEGREE_PLAN = """
            Fictional. Prototype data. Degree plan for the M.S. in Clinical Mental Health Counseling, 2026-27.
            The program requires a total of 60 semester credit hours for graduation.
            Students complete a 100 clock hour practicum and a 600 clock hour internship.
            """;

    @Autowired protected MockMvcTester mvc;
    @Autowired protected TestTenants tenants;
    @Autowired protected ObjectMapper json;
    @Autowired protected JobRunner runner;
    @Autowired protected LlmClient llmClient;

    protected FakeLlmClient fake;
    protected AssessmentFixtures fx;
    protected TestTenants.TenantWithUsers t;

    @BeforeEach
    void baseSetUp() {
        fake = (FakeLlmClient) llmClient;
        fake.reset();
        runner.runPending();
        fx = new AssessmentFixtures(mvc, json, runner, fake);
        t = tenants.create("assess");
    }
}
