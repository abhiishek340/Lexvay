package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexvay.assessment.Finding;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingMethod;
import com.lexvay.llm.LlmCallBlockedException;
import com.lexvay.llm.LlmCallRecorder;
import com.lexvay.llm.LlmGateway;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRefusedException;
import com.lexvay.llm.LlmRequest;
import com.lexvay.llm.LlmResponse;
import com.lexvay.rule.RequirementType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** The generic baseline's prompt, budget and quote check, with the gateway and the recorder stubbed: no model, no database. */
class GenericBaselineTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final UUID ASSESSMENT = UUID.randomUUID();
    static final String ETHICS = "Students examine the ACA Code of Ethics, state licensing law, confidentiality and duty to warn.";

    static final EvalRepository.PageText SYLLABUS = new EvalRepository.PageText(UUID.randomUUID(), "coun605-syllabus.pdf", "a".repeat(64), true, 1,
            "COUN 605\n" + ETHICS + "\nWeek 1 Professional identity.");
    static final EvalRepository.PageText ELECTIVE = new EvalRepository.PageText(UUID.randomUUID(), "coun650-syllabus.pdf", "b".repeat(64), false, 1,
            "This elective covers the etiology of substance use and addictive disorders and their treatment.");
    static final EvalRepository.PageText PLAN = new EvalRepository.PageText(UUID.randomUUID(), "degree-plan.pdf", "c".repeat(64), null, 1,
            "The program requires the completion of 60 semester credit hours.");

    final LlmGateway gateway = mock(LlmGateway.class);
    final LlmCallRecorder recorder = mock(LlmCallRecorder.class);

    GenericBaseline baseline(int tokenBudget) {
        return new GenericBaseline(gateway, recorder, JSON, tokenBudget, 1_000);
    }

    static Mapping mapping(String jurisdiction, String ref) {
        var view = new Mapping.RequirementView(UUID.randomUUID(), ref, "Title of " + ref, "Text of " + ref, RequirementType.SUBJECT, null, null, null);
        return new Mapping(UUID.randomUUID(), ASSESSMENT, UUID.randomUUID(), jurisdiction, UUID.randomUUID(), view, Finding.EVIDENCE_MISSING,
                MappingMethod.MATCH, "", false, null, null, 0);
    }

    static String answer(String finding, String document, Integer page, String quote) {
        return JSON.createObjectNode().put("finding", finding).put("document", document).put("page_number", page).put("quote", quote).toString();
    }

    void reply(String text) {
        when(gateway.complete(any())).thenReturn(new LlmResponse(text, "model-x", 100, 20, 5));
    }

    @Test
    void theDocumentsAreGivenInFileOrderWithTheirPagesUpToTheTokenBudget() {
        var all = List.of(SYLLABUS, ELECTIVE, PLAN);

        var whole = GenericBaseline.Corpus.within(all, 10_000);
        assertThat(whole.pages()).isEqualTo(all);
        assertThat(whole.text()).startsWith("DOCUMENTS\n").contains("=== coun605-syllabus.pdf (page 1) ===", "=== coun650-syllabus.pdf (page 1) ===",
                "=== degree-plan.pdf (page 1) ===", ETHICS);

        int firstPageBlock = SYLLABUS.filename().length() + 20 + SYLLABUS.text().length();
        var cut = GenericBaseline.Corpus.within(all, ("DOCUMENTS\n".length() + firstPageBlock) / 4);
        assertThat(cut.pages()).containsExactly(SYLLABUS);
        assertThat(cut.pagesTotal()).isEqualTo(3);
        assertThat(cut.text()).doesNotContain("coun650");
    }

    @Test
    void oneGenericPromptPerRequirementAsksForTheFindingAndAQuoteAndCarriesNoPipelineRules() {
        reply(answer("EVIDENCE_MISSING", null, null, null));
        var m = mapping("UT", "UT-9");

        baseline(10_000).run(ASSESSMENT, List.of(m), List.of(SYLLABUS));

        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(gateway).complete(request.capture());
        assertThat(request.getValue().purpose()).isEqualTo(LlmPurpose.MATCH_EVIDENCE);
        assertThat(request.getValue().scriptKey()).isEqualTo("BASELINE:UT-9");
        assertThat(request.getValue().userPrompt()).contains(ETHICS, "REQUIREMENT UT-9\nTitle of UT-9\nText of UT-9");
        assertThat(request.getValue().systemPrompt()).contains("EVIDENCE_FOUND", "EVIDENCE_MISSING", "AMBIGUOUS", "word for word")
                .doesNotContainIgnoringCase("elective").doesNotContainIgnoringCase("when unsure");
        verify(recorder).record(eq(request.getValue()), any(), eq("eval-baseline-v1"), eq(m.ruleVersionId()), eq(ASSESSMENT), eq(m.requirement().id()));
    }

    @Test
    void aQuoteOnTheStoredPageIsLocatedAndCarriesTheSha256AndWhetherEveryStudentTakesTheCourse() {
        reply(answer("EVIDENCE_FOUND", "coun650-syllabus.pdf", 1, "elective covers the etiology  of substance use"));

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-8")), List.of(SYLLABUS, ELECTIVE));

        LabScorer.Answer answer = run.answers().get("UT/UT-8");
        assertThat(answer.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
        assertThat(answer.cited()).isEqualTo(new LabScorer.Cited("b".repeat(64), 1, 5, 50, false));
        assertThat(ELECTIVE.text().substring(5, 50)).isEqualTo("elective covers the etiology of substance use");
        assertThat(run.quoteVerification()).isEqualTo(new Ratio(1, 1));
        assertThat(run.pagesGiven()).isEqualTo(2);
    }

    @Test
    void aQuoteThatIsNotOnThePageLocatesNothingButTheClaimedFindingStandsAndTheQuoteCountsAgainstTheModel() {
        reply(answer("EVIDENCE_FOUND", "coun605-syllabus.pdf", 1, "Students must complete a course on the Utah practice act."));

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-9")), List.of(SYLLABUS));

        assertThat(run.answers().get("UT/UT-9")).isEqualTo(new LabScorer.Answer(Finding.EVIDENCE_FOUND, null));
        assertThat(run.quoteVerification()).isEqualTo(new Ratio(0, 1));
    }

    @Test
    void aQuoteFromADocumentOrPageTheModelWasNotShownIsNotLocated() {
        reply(answer("EVIDENCE_FOUND", "coun605-syllabus.pdf", 2, ETHICS));

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-2")), List.of(SYLLABUS));

        assertThat(run.answers().get("UT/UT-2").cited()).isNull();
        assertThat(run.quoteVerification()).isEqualTo(new Ratio(0, 1));
    }

    @Test
    void aPageCutByTheBudgetCannotBeCited() {
        reply(answer("EVIDENCE_FOUND", "degree-plan.pdf", 1, "The program requires the completion of 60 semester credit hours."));

        var run = baseline(10).run(ASSESSMENT, List.of(mapping("UT", "UT-1")), List.of(SYLLABUS, PLAN));

        assertThat(run.pagesGiven()).isZero();
        assertThat(run.pagesTotal()).isEqualTo(2);
        assertThat(run.answers().get("UT/UT-1").cited()).isNull();
    }

    @Test
    void aMissingFindingCitesNothingAndCountsNoQuote() {
        reply(answer("EVIDENCE_MISSING", null, null, null));

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-9")), List.of(SYLLABUS));

        assertThat(run.answers().get("UT/UT-9")).isEqualTo(new LabScorer.Answer(Finding.EVIDENCE_MISSING, null));
        assertThat(run.quoteVerification()).isEqualTo(new Ratio(0, 0));
        assertThat(run.unusableAnswers()).isZero();
    }

    @Test
    void anAnswerOutsideTheSchemaIsUnusableAndNeverAFinding() {
        reply("{\"finding\":\"MEETS\",\"document\":null,\"page_number\":null,\"quote\":null}");

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-9")), List.of(SYLLABUS));

        assertThat(run.answers().get("UT/UT-9")).isEqualTo(LabScorer.Answer.NONE);
        assertThat(run.unusableAnswers()).isEqualTo(1);
    }

    @Test
    void aModelThatDeclinesLeavesTheRequirementUnansweredAndStoresNoCall() {
        when(gateway.complete(any())).thenThrow(new LlmRefusedException("declined"));

        var run = baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-9")), List.of(SYLLABUS));

        assertThat(run.answers().get("UT/UT-9")).isEqualTo(LabScorer.Answer.NONE);
        assertThat(run.unusableAnswers()).isEqualTo(1);
        verify(recorder, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aCallTheProductRefusesEndsTheRunSoTheSpendGateCannotBeWalkedAround() {
        when(gateway.complete(any())).thenThrow(new LlmCallBlockedException(LlmCallBlockedException.Reason.PAID_CALLS_NOT_APPROVED,
                "Paid model calls are not approved"));

        assertThatThrownBy(() -> baseline(10_000).run(ASSESSMENT, List.of(mapping("UT", "UT-9"), mapping("UT", "UT-8")), List.of(SYLLABUS)))
                .isInstanceOf(LlmCallBlockedException.class);
        verify(gateway).complete(any());
    }
}
