package com.lexvay.seed;

import com.lexvay.common.YamlFile;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * Scripts {@link FakeLlmClient} from seed/fake-llm.yml, resolving document file names to the ids of the
 * documents just uploaded (D-061).
 */
public final class ScriptedFakeModel implements ModelStrategy {

    private final FakeLlmClient fake;
    private final FakeLlmScripts scripts;
    private final ObjectMapper json;

    private ScriptedFakeModel(FakeLlmClient fake, FakeLlmScripts scripts, ObjectMapper json) {
        this.fake = fake;
        this.scripts = scripts;
        this.json = json;
    }

    /** Fails unless the configured model is the fake one: the scripts mean nothing to a real model. */
    public static ScriptedFakeModel load(Path seedDir, LlmClient llm, ObjectMapper json) {
        if (!(llm instanceof FakeLlmClient fake)) {
            throw new IllegalStateException("The seed scripts the model; run it with LEXVAY_LLM_PROVIDER=fake");
        }
        return new ScriptedFakeModel(fake, YamlFile.read(seedDir.resolve("fake-llm.yml"), FakeLlmScripts.class), json);
    }

    @Override
    public void beforeExtraction(String ruleSetLabel) {
        var extraction = scripts.extraction().get(ruleSetLabel);
        if (extraction == null) {
            throw new IllegalStateException("fake-llm.yml has no extraction script for '" + ruleSetLabel + "'");
        }
        fake.replaceScript("EXTRACT:" + ruleSetLabel, json.writeValueAsString(extraction));
    }

    @Override
    public void beforeMatching(Map<String, UUID> documentByFile) {
        for (var entry : scripts.match().entrySet()) {
            FakeLlmScripts.MatchScript m = entry.getValue();
            Map<String, Object> out = new HashMap<>();
            out.put("finding", m.finding());
            out.put("document_id", m.document() == null ? null : requireDocument(documentByFile, m.document()).toString());
            out.put("page_number", m.page());
            out.put("quote", m.quote());
            out.put("rationale", m.rationale());
            fake.replaceScript("MATCH:" + entry.getKey(), json.writeValueAsString(out));
        }
    }

    private static UUID requireDocument(Map<String, UUID> byFile, String file) {
        UUID id = byFile.get(file);
        if (id == null) {
            throw new IllegalStateException("fake-llm.yml refers to unknown document " + file);
        }
        return id;
    }
}
