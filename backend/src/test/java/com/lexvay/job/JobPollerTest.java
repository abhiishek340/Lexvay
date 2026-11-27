package com.lexvay.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** Once shutdown starts the poller claims nothing further; the job in hand is the runner's to finish. */
class JobPollerTest {

    @Test
    void pollsUntilShutdownBeginsThenClaimsNothing() {
        JobRunner runner = mock(JobRunner.class);
        List<Boolean> mayClaim = new ArrayList<>();
        when(runner.runWhile(any())).thenAnswer(invocation -> {
            BooleanSupplier supplier = invocation.getArgument(0);
            mayClaim.add(supplier.getAsBoolean());
            return 0;
        });
        JobPoller poller = new JobPoller(runner);

        poller.poll();
        poller.stopClaiming();
        poller.poll();

        assertThat(mayClaim).containsExactly(true, false);
    }
}
