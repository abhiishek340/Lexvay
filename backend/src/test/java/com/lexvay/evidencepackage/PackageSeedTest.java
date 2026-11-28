package com.lexvay.evidencepackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestCsv;
import com.lexvay.document.DocumentService;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedCopy;
import com.lexvay.seed.SeedLoader;
import com.lexvay.seed.SeedSigning;
import com.lexvay.tenant.AppUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The package of a determination signed on the seeded demo program, whose documents are real PDFs. */
class PackageSeedTest extends AbstractDatabaseTest {

    private static final String EMAIL_DOMAIN = "package-seed.example";
    private static final List<String> TARGETS = List.of("AZ", "CA", "CO", "TX", "UT");

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    @Test
    void aSeededSignedDeterminationIsPackagedWithItsOriginalPdfsAndVerifies() throws Exception {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
        var seed = SeedCopy.northfield("Package Seed University", EMAIL_DOMAIN);
        SeedLoader.Result loaded = loader.load(seed, "demo-pass", ScriptedFakeModel.load(seed, llm, json)).get(0);
        LexvayUser dana = users.findForLogin("dana.whitfield@" + EMAIL_DOMAIN).orElseThrow();
        LexvayUser priya = users.findForLogin("priya.raman@" + EMAIL_DOMAIN).orElseThrow();
        UUID determinationId = SeedSigning.signFirstCurrentJurisdiction(mvc, json, TARGETS, loaded.assessmentId(), loaded.programId(), priya, dana)
                .determinationId();

        var packages = new PackageFixtures(mvc, json, runner);
        packages.readyReport(dana, determinationId);
        UUID packageId = packages.acceptedPackage(dana, determinationId);
        runner.runPending();
        JsonNode status = packages.status(priya, packageId);
        assertThat(status.get("status").asString()).isEqualTo("READY");
        byte[] zip = packages.file(priya, packageId).getResponse().getContentAsByteArray();
        assertThat(DocumentService.sha256(zip)).isEqualTo(status.get("sha256").asString());

        Map<String, byte[]> entries = PackageFixtures.unzip(zip);
        packages.checkedManifest(entries);
        assertThat(entries.keySet()).startsWith("README.txt", "report.pdf", "determination.json", "requirements.csv", "quotes.csv", "audit.csv")
                .endsWith("manifest.json");
        List<String> documents = entries.keySet().stream().filter(name -> name.startsWith("documents/")).toList();
        assertThat(documents).isNotEmpty().allSatisfy(name -> assertThat(entries.get(name)).as(name).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII)));
        List<List<String>> quotes = TestCsv.parse(new String(entries.get("quotes.csv"), StandardCharsets.UTF_8));
        assertThat(quotes.subList(1, quotes.size())).allSatisfy(row -> assertThat(entries).containsKey("documents/" + row.get(1)));
        assertThat(entries.get("report.pdf")).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
    }
}
