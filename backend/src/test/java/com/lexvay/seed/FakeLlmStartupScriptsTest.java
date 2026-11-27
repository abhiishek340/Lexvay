package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.llm.LlmConfig;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRequest;
import com.lexvay.llm.LlmResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class FakeLlmStartupScriptsTest {

    static final String LABEL = "Demo rule set - Test (fictional) 2027";

    JsonMapper json = JsonMapper.builder().build();
    @TempDir Path dir;

    Path file(String name, String yaml) throws IOException {
        return Files.writeString(dir.resolve(name), yaml);
    }

    String answer(FakeLlmClient fake, String label) {
        return fake.complete(new LlmRequest(LlmPurpose.EXTRACT_REQUIREMENTS, "EXTRACT:" + label, "sys", "user",
                json.createObjectNode(), 100)).text();
    }

    @Test
    void scriptsTheClientFromTwoFilesInOrderAndTheLaterFileWins() throws IOException {
        Path first = file("first.yml", """
                extraction:
                  "Label A": {"requirements": [{"ref": "A-1"}]}
                  "%s": {"requirements": [{"ref": "FIRST-1"}]}
                match:
                  "A-1": {finding: EVIDENCE_MISSING, document: null, page: null, quote: null, rationale: "none"}
                """.formatted(LABEL));
        Path second = file("second.yml", """
                extraction:
                  "%s": {"requirements": [{"ref": "SECOND-1"}]}
                """.formatted(LABEL));
        FakeLlmClient fake = new FakeLlmClient();

        new FakeLlmStartupScripts(fake, json, List.of(first.toString(), second.toString())).load();

        assertThat(answer(fake, "Label A")).contains("A-1");
        assertThat(answer(fake, LABEL)).contains("SECOND-1").doesNotContain("FIRST-1");
    }

    @Test
    void loadingTheSameLabelTwiceDoesNotQueueTheEarlierAnswer() throws IOException {
        Path script = file("one.yml", "extraction:\n  \"" + LABEL + "\": {\"requirements\": [{\"ref\": \"ONE-1\"}]}\n");
        FakeLlmClient fake = new FakeLlmClient();

        new FakeLlmStartupScripts(fake, json, List.of(script.toString(), script.toString())).load();

        assertThat(answer(fake, LABEL)).contains("ONE-1");
        assertThat(answer(fake, LABEL)).contains("ONE-1");
    }

    @Test
    void noFilesLeavesTheClientUnscripted() {
        FakeLlmClient fake = new FakeLlmClient();

        new FakeLlmStartupScripts(fake, json, List.of()).load();
        new FakeLlmStartupScripts(fake, json, List.of("")).load();

        assertThatThrownBy(() -> answer(fake, LABEL)).hasMessageContaining("no scripted response");
    }

    @Test
    void aMissingFileFailsStartupNamingIt() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeLlmStartupScripts scripts = new FakeLlmStartupScripts(fake, json, List.of(dir.resolve("absent.yml").toString()));

        assertThatThrownBy(scripts::load).hasMessageContaining("absent.yml");
    }

    @Test
    void anUnknownProviderWithScriptsFailsStartupNamingTheProvider() throws IOException {
        Path script = file("one.yml", "extraction: {}\n");
        contextRunner(script, "openai").run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause().hasMessageContaining("Unknown LEXVAY_LLM_PROVIDER value: openai"));
    }

    @Test
    void aClientThatIsNotTheFakeOneIgnoresTheScriptsWithOneWarningAndIsNeverCalled(CapturedOutput output) throws IOException {
        Path script = file("one.yml", "extraction:\n  \"" + LABEL + "\": {\"requirements\": [{\"ref\": \"ONE-1\"}]}\n");
        List<LlmRequest> calls = new ArrayList<>();
        LlmClient real = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                calls.add(request);
                throw new AssertionError("a network call");
            }

            @Override
            public String modelId() {
                return "claude-test";
            }
        };

        new FakeLlmStartupScripts(real, json, List.of(script.toString())).load();

        assertThat(calls).isEmpty();
        assertThat(output.getAll()).containsOnlyOnce("lexvay.llm.fake-scripts is set but the model is claude-test");
    }

    @Test
    void scriptedModelRefusesAClientThatIsNotTheFakeOne() throws IOException {
        Path script = file("one.yml", "extraction: {}\n");
        LlmClient real = new LlmClient() {
            @Override
            public LlmResponse complete(LlmRequest request) {
                throw new AssertionError("a network call");
            }

            @Override
            public String modelId() {
                return "claude-test";
            }
        };

        assertThatThrownBy(() -> ScriptedFakeModel.load(List.of(script), real, json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEXVAY_LLM_PROVIDER=fake");
    }

    @Test
    void theAnthropicProviderIgnoresTheProperty(CapturedOutput output) throws IOException {
        Path script = file("one.yml", "extraction:\n  \"" + LABEL + "\": {\"requirements\": [{\"ref\": \"ONE-1\"}]}\n");
        contextRunner(script, "anthropic").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LlmClient.class)).isNotInstanceOf(FakeLlmClient.class);
        });
        assertThat(output.getAll()).containsOnlyOnce("lexvay.llm.fake-scripts is set but the model is");
    }

    ApplicationContextRunner contextRunner(Path script, String provider) {
        return new ApplicationContextRunner()
                .withUserConfiguration(LlmConfig.class, FakeLlmStartupScripts.class)
                .withBean(ObjectMapper.class, () -> json)
                .withPropertyValues("lexvay.llm.provider=" + provider, "lexvay.llm.fake-scripts=" + script);
    }
}
