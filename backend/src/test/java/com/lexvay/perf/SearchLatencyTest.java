package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.search.AbstractSearchTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Budget in CLAUDE.md: p95 under 40 ms for read endpoints on the seeded tenant. Measured after a warm-up. */
@Tag("perf")
class SearchLatencyTest extends AbstractSearchTest {

    static final int WARM_UP = 50;
    static final int REQUESTS = 200;
    static final long BUDGET_MS = 40;

    @Test
    void searchP95IsWithinBudgetOverTwoHundredRequests() {
        List<String> queries = List.of("\"duty to warn\"", "ethics -group", "counseling OR practicum", "crisis response",
                "supervised clinical hours", "diagnosis treatment planning");
        for (int i = 0; i < WARM_UP; i++) {
            get(queries.get(i % queries.size()));
        }
        long[] millis = new long[REQUESTS];
        for (int i = 0; i < REQUESTS; i++) {
            long start = System.nanoTime();
            get(queries.get(i % queries.size()));
            millis[i] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(millis);
        long p95 = millis[(int) Math.ceil(REQUESTS * 0.95) - 1];
        System.out.println("search latency over " + REQUESTS + " requests: p50=" + millis[REQUESTS / 2] + " ms, p95=" + p95
                + " ms, max=" + millis[REQUESTS - 1] + " ms");
        assertThat(p95).as("p95 latency in ms").isLessThanOrEqualTo(BUDGET_MS);
    }

    private void get(String query) {
        assertThat(searchAs(tenant.reviewer(), searchUri(programId, query, ""))).hasStatus(200);
    }
}
