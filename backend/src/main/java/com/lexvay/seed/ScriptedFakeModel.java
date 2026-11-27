package com.lexvay.seed;

import com.lexvay.common.YamlFile;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/**
 * Scripts {@link FakeLlmClient} from fake-llm.yml files, resolving document file names to the ids of the
 * documents just uploaded (D-061). The seed loads seed/fake-llm.yml; the application can load more files at
 * startup ({@link FakeLlmStartupScripts}).
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
        return load(List.of(seedDir.resolve("fake-llm.yml")), llm, json);
    }

    /** Reads the files in order; where two script the same key, the later file wins. */
    public static ScriptedFakeModel load(List<Path> files, LlmClient llm, ObjectMapper json) {
        if (!(llm instanceof FakeLlmClient fake)) {
            throw new IllegalStateException("Scripting the model needs LEXVAY_LLM_PROVIDER=fake");
        }
        FakeLlmScripts merged = new FakeLlmScripts(null, null);
        for (Path file : files) {
            merged = merged.overriddenBy(YamlFile.read(file, FakeLlmScripts.class));
        }
        return new ScriptedFakeModel(fake, merged, json);
    }

    /** Scripts every extraction at once; match scripts need document ids, which exist only once a seed has uploaded them. */
    public void scriptAllExtractions() {
        scripts.extraction().keySet().forEach(this::beforeExtraction);
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
