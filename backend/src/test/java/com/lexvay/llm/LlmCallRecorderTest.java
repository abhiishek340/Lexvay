package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** What is stored with a call, against the real database. */
class LlmCallRecorderTest extends AbstractDatabaseTest {

    static final LlmRequest REQUEST = new LlmRequest(LlmPurpose.MATCH_EVIDENCE, "MATCH:X-1", "system", "user prompt",
            JsonMapper.builder().build().createObjectNode(), 100);

    @Autowired LlmCallRecorder recorder;
    @Autowired TestTenants tenants;
    @Autowired JdbcClient jdbc;

    /** The {@code tokensEstimated} entry of the request stored for the call, or "none" when there is none. */
    private String storedMark(LlmResponse response) {
        UUID tenantId = tenants.create("recorder-" + UUID.randomUUID().toString().substring(0, 8)).tenantId();
        return TenantContext.runAs(tenantId, () -> {
            UUID id = recorder.record(REQUEST, response, "match-v1", null, null, null);
            return jdbc.sql("SELECT coalesce(request ->> 'tokensEstimated', 'none') FROM llm_call WHERE id = :id").param("id", id)
                    .query(String.class).single();
        });
    }

    @Test
    void aCallWhoseTokenCountsWereEstimatedSaysSoInItsStoredRequest() {
        assertThat(storedMark(new LlmResponse("{}", "local-model", 3, 1, 5, true))).isEqualTo("true");
    }

    @Test
    void aCallWithReportedTokenCountsCarriesNoMark() {
        assertThat(storedMark(new LlmResponse("{}", "local-model", 3, 1, 5))).isEqualTo("none");
    }
}
