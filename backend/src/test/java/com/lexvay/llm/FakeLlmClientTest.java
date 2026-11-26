package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FakeLlmClientTest {

    FakeLlmClient fake = new FakeLlmClient();
    LlmRequest request(String key) {
        return new LlmRequest(LlmPurpose.EXTRACT_REQUIREMENTS, key, "sys", "user", JsonMapper.builder().build().createObjectNode(), 100);
    }

    @Test
    void scriptedResponsesComeInOrderAndTheLastOneRepeats() {
        fake.script("EXTRACT:demo", "first", "second");
        assertThat(fake.complete(request("EXTRACT:demo")).text()).isEqualTo("first");
        assertThat(fake.complete(request("EXTRACT:demo")).text()).isEqualTo("second");
        assertThat(fake.complete(request("EXTRACT:demo")).text()).isEqualTo("second");
        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).isEqualTo(3);
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isZero();
        assertThat(fake.modelId()).isEqualTo("fake-llm");
    }

    @Test
    void missingScriptFailsLoudly() {
        assertThatThrownBy(() -> fake.complete(request("EXTRACT:unknown")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EXTRACT:unknown");
    }

    @Test
    void resetClearsScriptsAndCalls() {
        fake.script("k", "v");
        fake.complete(request("k"));
        fake.reset();
        assertThat(fake.calls()).isEmpty();
        assertThatThrownBy(() -> fake.complete(request("k"))).isInstanceOf(IllegalStateException.class);
    }
}
