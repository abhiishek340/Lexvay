package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRates;
import com.lexvay.llm.LlmUsageRepository;
import com.lexvay.llm.LlmUsageRepository.PurposeUsage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssessmentUsageServiceTest {

    private final UUID id = UUID.randomUUID();
    private final AssessmentService assessments = mock(AssessmentService.class);
    private final LlmUsageRepository usage = mock(LlmUsageRepository.class);

    private AssessmentUsageService service(String rates) {
        OffsetDateTime start = OffsetDateTime.parse("2026-11-26T10:00:00Z");
        when(assessments.require(id)).thenReturn(new Assessment(id, UUID.randomUUID(), AssessmentStatus.FINISHED, start,
                start.plusSeconds(3), "model-x", "match-v1", start, null));
        when(usage.forAssessment(id)).thenReturn(List.of(
                new PurposeUsage(LlmPurpose.MATCH_EVIDENCE, 4, 1_500_000, 250_000, 1200)));
        return new AssessmentUsageService(assessments, usage, LlmRates.parse(rates));
    }

    @Test
    void costIsTokensTimesThePricePerMillion() {
        var view = service("model-x=3.00:15.00").usage(id);

        assertThat(view.estimatedCostUsd()).isEqualByComparingTo("8.250000");
        assertThat(view.wallTimeMs()).isEqualTo(3000);
        assertThat(view.modelTimeMs()).isEqualTo(1200);
        assertThat(view.calls()).isEqualTo(4);
    }

    @Test
    void withNoPriceConfiguredForTheModelTheCostIsAbsentRatherThanZero() {
        var view = service("another-model=3.00:15.00").usage(id);

        assertThat(view.estimatedCostUsd()).isNull();
        assertThat(view.prices()).isNull();
        assertThat(view.inputTokens()).isEqualTo(1_500_000);
    }
}
