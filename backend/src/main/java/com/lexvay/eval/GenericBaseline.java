package com.lexvay.eval;

import com.lexvay.assessment.Finding;
import com.lexvay.assessment.Mapping;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.llm.JsonSchemaValidator;
import com.lexvay.llm.LlmCallBlockedException;
import com.lexvay.llm.LlmCallRecorder;
import com.lexvay.llm.LlmGateway;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRefusedException;
import com.lexvay.llm.LlmRequest;
import com.lexvay.llm.LlmResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What a strong generic model does on its own, so the pipeline's worth can be shown against it. The same model, the same
 * approved documents (their full text, in file order, up to a token budget) and one generic prompt per requirement that asks
 * for the finding and a quote. There is no retrieval, no downgrade rule, no rules engine and no pathway logic. Only the quote
 * check is shared: the model's quote is looked up in the stored page with {@link QuoteVerifier}, and a quote that is not there
 * locates nothing, so it can neither match a key's span nor support a finding. The finding the model claimed is still scored as
 * claimed. Every call goes through {@link LlmGateway}, so the spend gate applies, and is stored with the prompt version
 * {@link #PROMPT_VERSION} so its use is counted apart from the pipeline's. {@code llm_call.purpose} admits only the two purposes
 * of the pipeline and EV-2 has no migration, so the calls carry {@link LlmPurpose#MATCH_EVIDENCE}.
 */
@Component
class GenericBaseline {

    static final String PROMPT_VERSION = "eval-baseline-v1";
    private static final String SCHEMA = "llm/eval-baseline.schema.json";

    /** The answers by requirement key, how many quotes the model gave and how many of them are on the stored page. */
    record Run(Map<String, LabScorer.Answer> answers, Ratio quoteVerification, int unusableAnswers, int pagesGiven, int pagesTotal) {
    }

    /** The documents as the prompt gives them: the pages that fit the budget, as one block of text. */
    record Corpus(String text, List<EvalRepository.PageText> pages, int pagesTotal) {

        static Corpus within(List<EvalRepository.PageText> all, int tokenBudget) {
            StringBuilder text = new StringBuilder("DOCUMENTS\n");
            List<EvalRepository.PageText> given = new ArrayList<>();
            for (EvalRepository.PageText page : all) {
                String block = "\n=== " + page.filename() + " (page " + page.page() + ") ===\n" + page.text() + "\n";
                if ((text.length() + block.length()) / 4 > tokenBudget) {
                    break;
                }
                text.append(block);
                given.add(page);
            }
            return new Corpus(text.toString(), given, all.size());
        }
    }

    private final LlmGateway llm;
    private final LlmCallRecorder calls;
    private final JsonSchemaValidator validator;
    private final int tokenBudget;
    private final int maxOutputTokens;
    private final String systemPrompt;

    GenericBaseline(LlmGateway llm, LlmCallRecorder calls, ObjectMapper json,
                    @Value("${lexvay.eval.baseline-token-budget:24000}") int tokenBudget,
                    @Value("${lexvay.llm.max-output-tokens:16000}") int maxOutputTokens) {
        this.llm = llm;
        this.calls = calls;
        this.validator = new JsonSchemaValidator(json, SCHEMA);
        this.tokenBudget = tokenBudget;
        this.maxOutputTokens = maxOutputTokens;
        try {
            this.systemPrompt = new String(new ClassPathResource("llm/" + PROMPT_VERSION + ".system.txt").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Asks the model about every requirement of the assessment. Runs inside the evaluation tenant. A call the product refuses
     * ({@link LlmCallBlockedException}) ends the run and is the caller's to report.
     */
    Run run(UUID assessmentId, List<Mapping> mappings, List<EvalRepository.PageText> pages) {
        Corpus corpus = Corpus.within(pages, tokenBudget);
        Map<String, LabScorer.Answer> answers = new LinkedHashMap<>();
        int claimed = 0;
        int verified = 0;
        int unusable = 0;
        for (Mapping m : mappings) {
            Optional<JsonNode> reply = ask(assessmentId, m, corpus);
            if (reply.isEmpty()) {
                unusable++;
                answers.put(Golden.key(m.jurisdiction(), m.requirement().ref()), LabScorer.Answer.NONE);
                continue;
            }
            JsonNode node = reply.get();
            Finding finding = Finding.valueOf(node.path("finding").asString());
            LabScorer.Cited cited = null;
            if (finding != Finding.EVIDENCE_MISSING && !node.path("quote").isNull()) {
                claimed++;
                cited = locate(corpus, node).orElse(null);
                verified += cited == null ? 0 : 1;
            }
            answers.put(Golden.key(m.jurisdiction(), m.requirement().ref()), new LabScorer.Answer(finding, cited));
        }
        return new Run(answers, new Ratio(verified, claimed), unusable, corpus.pages().size(), corpus.pagesTotal());
    }

    /** The model's answer, or empty when it declined or did not answer in the schema. */
    private Optional<JsonNode> ask(UUID assessmentId, Mapping m, Corpus corpus) {
        LlmRequest request = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "BASELINE:" + m.requirement().ref(), systemPrompt,
                userPrompt(corpus, m.requirement()), validator.schema(), maxOutputTokens);
        LlmResponse response;
        try {
            response = llm.complete(request);
        } catch (LlmCallBlockedException blocked) {
            throw blocked; // a refusal by the product, not by the model: it ends the run
        } catch (LlmRefusedException declined) {
            return Optional.empty();
        }
        calls.record(request, response, PROMPT_VERSION, m.ruleVersionId(), assessmentId, m.requirement().id());
        JsonSchemaValidator.Result result = validator.validate(response.text());
        return result.valid() ? Optional.of(result.value()) : Optional.empty();
    }

    /** The cited passage found in the stored page of a document the model was shown; empty when it is not there. */
    private static Optional<LabScorer.Cited> locate(Corpus corpus, JsonNode answer) {
        String file = answer.path("document").asString("");
        int pageNumber = answer.path("page_number").asInt(0);
        return corpus.pages().stream().filter(p -> p.filename().equals(file) && p.page() == pageNumber).findFirst()
                .flatMap(page -> QuoteVerifier.find(page.text(), answer.path("quote").asString(""))
                        .map(match -> new LabScorer.Cited(page.sha256(), page.page(), match.charStart(), match.charEnd(),
                                !Boolean.FALSE.equals(page.courseRequired()))));
    }

    static String userPrompt(Corpus corpus, Mapping.RequirementView requirement) {
        return corpus.text() + "\nREQUIREMENT " + requirement.ref() + "\n" + requirement.title() + "\n" + requirement.text() + "\n";
    }
}
