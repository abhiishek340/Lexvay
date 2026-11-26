package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.document.FileType;
import com.lexvay.document.TextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Spec section 1: all seed content is fictional and says so, in the data and on the first page of every document. */
class SeedIsLabeledFictionalTest {

    static final Path SEED = Path.of("..", "seed");

    @Test
    void everySeedPdfSaysFictionalOnPageOne() throws IOException {
        try (Stream<Path> files = Files.list(SEED.resolve("pdf"))) {
            var pdfs = files.filter(p -> p.toString().endsWith(".pdf")).toList();
            assertThat(pdfs).hasSize(14);
            for (Path pdf : pdfs) {
                var pages = TextExtractor.extract(FileType.PDF, Files.readAllBytes(pdf));
                assertThat(pages.get(0).text()).as(pdf.getFileName().toString()).contains("Fictional. Prototype data.");
            }
        }
    }

    @Test
    void everyRuleSetIsLabeledDemoAndFictional() throws IOException {
        var manifest = YAMLMapper.builder().build().readValue(Files.readString(SEED.resolve("manifest.yml")), SeedManifest.class);
        assertThat(manifest.fictional()).isTrue();
        for (var rv : manifest.tenants().get(0).ruleVersions()) {
            assertThat(rv.label()).startsWith("Demo rule set");
            assertThat(rv.sourceUrl()).startsWith("https://example.invalid/");
            assertThat(Files.readString(SEED.resolve("rules").resolve(rv.text()))).contains("Fictional").contains("Not the law of any state");
        }
        assertThat(manifest.tenants().get(0).changes().ruleVersion().label()).startsWith("Demo rule set");
    }
}
