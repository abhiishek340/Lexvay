package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.YamlFile;
import com.lexvay.rule.RequirementDraft;
import com.lexvay.rule.RequirementListParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The scripted extractions of the seed and of the end-to-end fixture carry hand-written search terms (F7c) that the production parser keeps whole. */
class SeedSearchTermsTest {

    static final Path SEED = Path.of("..", "seed", "fake-llm.yml");
    static final Path E2E = Path.of("..", "frontend", "e2e", "fixtures", "fake-llm.e2e.yml");

    final RequirementListParser parser = new RequirementListParser(JsonMapper.builder().build());

    private static Map<String, JsonNode> extractions(Path file) {
        return YamlFile.read(file, FakeLlmScripts.class).extraction();
    }

    @Test
    void everyScriptedRequirementHasAtLeastThreeTermsAndTheParserDropsNone() {
        for (Path file : List.of(SEED, E2E)) {
            extractions(file).forEach((label, script) -> {
                List<RequirementDraft> drafts = parser.parse(script);
                JsonNode raw = script.get("requirements");
                assertThat(drafts).as(label).hasSameSizeAs(raw);
                for (int i = 0; i < drafts.size(); i++) {
                    RequirementDraft draft = drafts.get(i);
                    assertThat(draft.searchTerms()).as(label + " " + draft.ref() + " terms").hasSizeBetween(3, 8)
                            .hasSize(raw.get(i).get("search_terms").size());
                }
            });
        }
    }

    @Test
    void theScriptedOutputIsValidAgainstTheExtractionSchema() {
        for (Path file : List.of(SEED, E2E)) {
            extractions(file).forEach((label, script) -> assertThat(parser.parse(script.toString())).as(label).isNotEmpty());
        }
    }

    @Test
    void theColoradoRevisionKeepsEveryRequirementsTermsSoTheDiffShowsOnlyTheChangedText() {
        Map<String, JsonNode> seed = extractions(SEED);
        List<RequirementDraft> first = parser.parse(seed.get("Demo rule set - Colorado (fictional) 2026"));
        List<RequirementDraft> second = parser.parse(seed.get("Demo rule set - Colorado (fictional) 2026 revision 2"));

        assertThat(second).hasSameSizeAs(first);
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i).searchTerms()).as(first.get(i).ref()).isEqualTo(first.get(i).searchTerms());
        }
    }
}
