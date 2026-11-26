package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Product rule 3: the rules engine has no path to the language model. Checked at the source level. */
class Rule3_ArchitectureTest {

    @Test
    void rulesEnginePackageNeverImportsTheModelSeam() throws IOException {
        Path rules = Path.of("src/main/java/com/lexvay/assessment/rules");
        assertThat(rules).isDirectory();
        try (Stream<Path> files = Files.walk(rules)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                String text = Files.readString(source);
                assertThat(text).as(source.toString()).doesNotContain("com.lexvay.llm").doesNotContain("LlmClient")
                        .doesNotContain("com.lexvay.assessment.match");
            }
        }
    }
}
