package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** Once shutdown starts the poller begins no further batch; the one in hand is the sealer's to finish. */
class AuditSealPollerTest {

    @Test
    void sealsUntilShutdownBeginsThenSealsNothingFurther() {
        AuditSealer sealer = mock(AuditSealer.class);
        List<Boolean> maySeal = new ArrayList<>();
        when(sealer.sealWhile(any())).thenAnswer(invocation -> {
            BooleanSupplier supplier = invocation.getArgument(0);
            maySeal.add(supplier.getAsBoolean());
            return 0;
        });
        AuditSealPoller poller = new AuditSealPoller(sealer);

        poller.poll();
        poller.stopSealing();
        poller.poll();

        assertThat(maySeal).containsExactly(true, false);
    }
}
