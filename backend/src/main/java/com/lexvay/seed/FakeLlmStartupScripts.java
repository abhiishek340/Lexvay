package com.lexvay.seed;

import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the fake-llm.yml files named by {@code lexvay.llm.fake-scripts} into the fake model when the application
 * starts, so the end-to-end backend can extract requirements for the rule versions the browser tests create (D-280).
 * Empty by default. With a real model the files mean nothing, so they are ignored with a warning.
 */
@Component
public class FakeLlmStartupScripts {

    private static final Logger log = LoggerFactory.getLogger(FakeLlmStartupScripts.class);

    private final LlmClient llm;
    private final ObjectMapper json;
    private final List<Path> files;

    public FakeLlmStartupScripts(LlmClient llm, ObjectMapper json, @Value("${lexvay.llm.fake-scripts:}") List<String> files) {
        this.llm = llm;
        this.json = json;
        this.files = files.stream().map(String::strip).filter(f -> !f.isEmpty()).map(Path::of).toList();
    }

    @PostConstruct
    void load() {
        if (files.isEmpty()) {
            return;
        }
        if (!(llm instanceof FakeLlmClient)) {
            log.warn("lexvay.llm.fake-scripts is set but the model is {}; the scripts are ignored", llm.modelId());
            return;
        }
        ScriptedFakeModel.load(files, llm, json).scriptAllExtractions();
        log.info("Scripted the fake model from {}", files);
    }
}
