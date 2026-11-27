package com.lexvay.rule;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.job.Job;
import com.lexvay.job.JobHandler;
import com.lexvay.job.JobType;
import com.lexvay.job.PermanentJobFailure;
import com.lexvay.llm.LlmCallRecorder;
import com.lexvay.llm.LlmGateway;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRequest;
import com.lexvay.llm.LlmResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Pipeline stage 2. Sends the rule text to the model with a strict schema, validates the output, retries
 * once with the errors, then fails the job. Requirements are stored unapproved; a reviewer approves the
 * list before any assessment can use it.
 */
@Component
public class ExtractRequirementsHandler implements JobHandler {

    public static final String PROMPT_VERSION = "extract-v1";

    private final RuleVersionService ruleVersions;
    private final RuleVersionRepository repository;
    private final LlmGateway llm;
    private final LlmCallRecorder calls;
    private final RequirementListParser parser;
    private final AuditService audit;
    private final int maxOutputTokens;
    private final String systemPrompt;

    public ExtractRequirementsHandler(RuleVersionService ruleVersions, RuleVersionRepository repository, LlmGateway llm,
                                      LlmCallRecorder calls, RequirementListParser parser, AuditService audit,
                                      @Value("${lexvay.llm.max-output-tokens:16000}") int maxOutputTokens) {
        this.ruleVersions = ruleVersions;
        this.repository = repository;
        this.llm = llm;
        this.calls = calls;
        this.parser = parser;
        this.audit = audit;
        this.maxOutputTokens = maxOutputTokens;
        this.systemPrompt = readResource("llm/" + PROMPT_VERSION + ".system.txt");
    }

    @Override
    public JobType type() {
        return JobType.EXTRACT_REQUIREMENTS;
    }

    @Override
    public void handle(Job job, Map<String, Object> payload) {
        UUID ruleVersionId = UUID.fromString(String.valueOf(payload.get("ruleVersionId")));
        RuleVersion version = repository.findById(ruleVersionId)
                .orElseThrow(() -> new PermanentJobFailure("Rule version " + ruleVersionId + " not found"));
        if (repository.countRequirements(ruleVersionId) > 0) {
            return; // a retried job after a partial failure: the list already exists
        }
        String ruleText = ruleVersions.ruleText(version);
        String userPrompt = userPrompt(version, ruleText);
        String scriptKey = "EXTRACT:" + version.label();

        List<RequirementDraft> drafts = null;
        String lastErrors = null;
        for (int attempt = 1; attempt <= 2 && drafts == null; attempt++) {
            String prompt = attempt == 1 ? userPrompt
                    : userPrompt + "\n\nYour previous output was rejected for these reasons:\n- "
                    + lastErrors + "\nReturn corrected JSON that matches the schema exactly.";
            LlmRequest request = new LlmRequest(LlmPurpose.EXTRACT_REQUIREMENTS, scriptKey, systemPrompt, prompt,
                    parser.schema(), maxOutputTokens);
            LlmResponse response = llm.complete(request);
            calls.record(request, response, PROMPT_VERSION, ruleVersionId, null, null);
            try {
                drafts = parser.parse(response.text());
            } catch (InvalidRequirementListException e) {
                lastErrors = String.join("\n- ", e.errors());
            }
        }
        if (drafts == null) {
            throw new PermanentJobFailure("Requirement extraction produced invalid output twice: " + lastErrors);
        }
        UUID tenantId = TenantContext.require();
        repository.insertRequirements(tenantId, ruleVersionId, drafts, version.createdBy());
        audit.recordAs(null, "REQUIREMENTS_EXTRACTED", "rule_version", ruleVersionId, Map.of(
                "jobId", job.id().toString(), "model", llm.modelId(), "promptVersion", PROMPT_VERSION,
                "requirementCount", drafts.size()));
    }

    static String userPrompt(RuleVersion version, String ruleText) {
        return "Jurisdiction: " + version.jurisdictionCode() + "\nLicense type: " + version.licenseType()
                + "\nRule set label: " + version.label() + "\nUse ref prefix: " + version.jurisdictionCode()
                + "\n\nRULE TEXT:\n" + ruleText;
    }

    private static String readResource(String location) {
        try {
            return new String(new ClassPathResource(location).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + location, e);
        }
    }
}
