package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.document.ExtractedPage;
import com.lexvay.document.FileType;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.document.NoOcrEngine;
import com.lexvay.document.TextExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Every quote the scripted model "returns" must exist verbatim in the committed seed PDFs, or the demo would show EVIDENCE_MISSING. */
class SeedQuotesExistTest {

    static final Path SEED = Path.of("..", "seed");
    final YAMLMapper yaml = YAMLMapper.builder().build();
    final Map<String, List<ExtractedPage>> cache = new HashMap<>();

    List<ExtractedPage> pages(String file) throws IOException {
        if (!cache.containsKey(file)) {
            cache.put(file, new TextExtractor(new NoOcrEngine(), 200).extract(FileType.PDF, Files.readAllBytes(SEED.resolve("pdf").resolve(file))));
        }
        return cache.get(file);
    }

    @Test
    void everyMatchQuoteExistsOnItsPageAndFoundQuotesAreNotThin() throws IOException {
        FakeLlmScripts scripts = yaml.readValue(Files.readString(SEED.resolve("fake-llm.yml")), FakeLlmScripts.class);
        assertThat(scripts.match()).hasSizeGreaterThanOrEqualTo(30);
        for (var e : scripts.match().entrySet()) {
            var m = e.getValue();
            if (m.finding().equals("EVIDENCE_MISSING")) {
                assertThat(m.quote()).as(e.getKey()).isNull();
                continue;
            }
            var page = pages(m.document()).stream().filter(p -> p.pageNumber() == m.page()).findFirst().orElseThrow();
            assertThat(QuoteVerifier.find(page.text(), m.quote())).as(e.getKey() + " quote in " + m.document()).isPresent();
            if (m.finding().equals("EVIDENCE_FOUND")) {
                assertThat(QuoteVerifier.normalizeWhitespace(m.quote()).length()).as(e.getKey() + " not thin").isGreaterThanOrEqualTo(40);
            }
        }
    }

    @Test
    void everyFactQuoteExists() throws IOException {
        SeedManifest manifest = yaml.readValue(Files.readString(SEED.resolve("manifest.yml")), SeedManifest.class);
        for (var tenant : manifest.tenants()) {
            if (tenant.facts() == null) {
                continue;
            }
            for (var f : tenant.facts()) {
                var page = pages(f.document()).stream().filter(p -> p.pageNumber() == f.page()).findFirst().orElseThrow();
                assertThat(QuoteVerifier.find(page.text(), f.quote())).as(f.key()).isPresent();
            }
        }
    }

    @Test
    void everyExtractionScriptMatchesARuleVersionAndHasUniqueRefs() throws IOException {
        SeedManifest manifest = yaml.readValue(Files.readString(SEED.resolve("manifest.yml")), SeedManifest.class);
        FakeLlmScripts scripts = yaml.readValue(Files.readString(SEED.resolve("fake-llm.yml")), FakeLlmScripts.class);
        int total = 0;
        for (var rv : manifest.tenants().get(0).ruleVersions()) {
            var extraction = scripts.extraction().get(rv.label());
            assertThat(extraction).as(rv.label()).isNotNull();
            var refs = new java.util.HashSet<String>();
            for (var r : extraction.path("requirements")) {
                assertThat(refs.add(r.path("ref").asString())).as("unique ref " + r.path("ref").asString()).isTrue();
                assertThat(r.path("ref").asString()).startsWith(rv.jurisdiction() + "-");
                total++;
            }
        }
        assertThat(total).as("spec section 11: 42 requirements").isEqualTo(42);
    }
}
