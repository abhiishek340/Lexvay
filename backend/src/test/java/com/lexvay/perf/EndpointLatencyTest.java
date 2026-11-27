package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.CountingDataSource;
import com.lexvay.common.TestHttpClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-path latency budget. Loads the fictional seed (then again as 30 renamed copies for a second, larger pass), signs in over real HTTP on a random port, warms
 * each endpoint with 50 requests and times 300 sequential ones. Prints p50, p95 and p99 and fails when a
 * read endpoint's p95 exceeds 40 ms. Excluded from the default run; execute it with {@code make perf}.
 */
@Tag("perf")
@Import(CountingDataSource.Config.class)
class EndpointLatencyTest extends AbstractDatabaseTest {

    static final int WARM_UP = 50;
    static final int MEASURED = 300;
    static final double P95_BUDGET_MS = 40.0;
    static final String PASSWORD = "perf-demo-password";
    static final int EXTRA_TENANTS = 30;
    static final Path SEED_DIR = Path.of("..", "seed");
    static final String SIGNER_EMAIL = "dana.whitfield@northfield.example";

    /** One measured unit: one request, or the several requests a screen needs today. */
    record Scenario(String name, List<String> paths) {
        Scenario(String name, String path) {
            this(name, List.of(path));
        }
    }

    record Result(Scenario scenario, double p50, double p95, double p99, double checkoutsPerRound, int wireBytes, int bodyBytes) {
    }

    @LocalServerPort int port;
    @Autowired SeedLoader loader;
    @Autowired LlmClient llm;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;

    @Test
    void everyReadEndpointMeetsTheP95Budget() throws Exception {
        ScriptedFakeModel model = ScriptedFakeModel.load(SEED_DIR, llm, json);
        List<SeedLoader.Result> seeded = loader.load(SEED_DIR, PASSWORD, model);
        SeedLoader.Result northfield = seeded.get(0);
        TestHttpClient api = new TestHttpClient(port);
        api.login(SIGNER_EMAIL, PASSWORD);
        Fixture fixture = new Fixture(api, json, northfield.programId(), northfield.assessmentId());
        fixture.decideAndSignCurrentJurisdictions();

        List<Result> seedOnly = measureAll(api, fixture);
        PerfSeed.loadCopies(loader, SEED_DIR, PASSWORD, model, EXTRA_TENANTS);
        analyze();
        List<Result> scaled = measureAll(api, fixture);

        String report = "Seed only (2 tenants):\n\n" + table(seedOnly)
                + "\nSeed plus " + EXTRA_TENANTS + " renamed copies of both tenants (" + (2 + 2 * EXTRA_TENANTS) + " tenants):\n\n" + table(scaled);
        System.out.println(report);
        Path out = Path.of("target", "perf", "latency.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);

        List<String> overBudget = Stream.concat(seedOnly.stream(), scaled.stream()).filter(r -> r.p95() > P95_BUDGET_MS)
                .map(r -> r.scenario().name() + " p95 " + format(r.p95()) + " ms").toList();
        assertThat(overBudget).as("read endpoints over the %.0f ms p95 budget", P95_BUDGET_MS).isEmpty();
    }

    private List<Result> measureAll(TestHttpClient api, Fixture fixture) {
        List<Result> results = new ArrayList<>();
        for (Scenario scenario : scenarios(fixture)) {
            results.add(measure(api, scenario));
        }
        return results;
    }

    /** Fresh planner statistics, as autovacuum would have after the load. */
    private static void analyze() throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), OWNER_USER, OWNER_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE");
        }
    }

    /** Every read endpoint the single-page app uses, with the first screen's parts listed separately. */
    private static List<Scenario> scenarios(Fixture f) {
        String program = "/api/v1/programs/" + f.programId;
        return List.of(
                new Scenario("GET /auth/me", "/api/v1/auth/me"),
                new Scenario("GET /jurisdictions", "/api/v1/jurisdictions"),
                new Scenario("GET /programs", "/api/v1/programs"),
                new Scenario("GET /programs/{id}", program),
                new Scenario("GET /programs/{id}/courses", program + "/courses"),
                new Scenario("GET /programs/{id}/facts", program + "/facts"),
                new Scenario("GET /programs/{id}/determinations", program + "/determinations"),
                new Scenario("GET /change-events", "/api/v1/change-events"),
                new Scenario("GET /rule-versions", "/api/v1/rule-versions"),
                new Scenario("GET /rule-versions/{id}/requirements", "/api/v1/rule-versions/" + f.ruleVersionId + "/requirements"),
                new Scenario("GET /assessments/{id}", "/api/v1/assessments/" + f.assessmentId),
                new Scenario("GET /assessments/{id}/mappings?jurisdiction=CA", "/api/v1/assessments/" + f.assessmentId + "/mappings?jurisdiction=CA"),
                new Scenario("GET /assessments/{id}/mappings (all)", "/api/v1/assessments/" + f.assessmentId + "/mappings"),
                new Scenario("GET /documents/{id}", "/api/v1/documents/" + f.documentId),
                new Scenario("GET /audit?limit=100", "/api/v1/audit?limit=100"),
                new Scenario("GET /audit?action=REVIEW_RECORDED&entityType=mapping", "/api/v1/audit?action=REVIEW_RECORDED&entityType=mapping"),
                new Scenario("GET /programs/{id}/overview", program + "/overview"),
                new Scenario("GET /programs/{id}/assessments", program + "/assessments"),
                new Scenario("GET /documents/{id}/pages/1", "/api/v1/documents/" + f.documentId + "/pages/1"),
                new Scenario("GET /mappings/{id}/reviews", "/api/v1/mappings/" + f.reviewedMappingId + "/reviews"),
                new Scenario("GET /mappings/{id}/evidence-responses", "/api/v1/mappings/" + f.reviewedMappingId + "/evidence-responses"),
                new Scenario("GET /programs/{id}/evidence-requests?status=ALL", program + "/evidence-requests?status=ALL"),
                new Scenario("GET /programs/{id}/search?q=ethics", program + "/search?q=ethics"),
                new Scenario("GET /rule-versions/{id}/diff", "/api/v1/rule-versions/" + f.supersedingRuleVersionId + "/diff"),
                new Scenario("GET /rule-sources", "/api/v1/rule-sources"),
                new Scenario("GET /rule-sources/{id}/checks", "/api/v1/rule-sources/" + f.ruleSourceId + "/checks"),
                new Scenario("GET /determinations/register", "/api/v1/determinations/register"),
                new Scenario("GET /determinations/register?programId=&current=false", "/api/v1/determinations/register?current=false&programId=" + f.programId),
                new Scenario("GET /programs/{id}/determination-lists", program + "/determination-lists"),
                new Scenario("Program screen today: program + determinations + change-events (3 requests)",
                        List.of(program, program + "/determinations", "/api/v1/change-events")));
    }

    private Result measure(TestHttpClient api, Scenario scenario) {
        for (int i = 0; i < WARM_UP; i++) {
            round(api, scenario);
        }
        CountingDataSource counter = (CountingDataSource) dataSource;
        long checkoutsBefore = counter.checkouts();
        double[] millis = new double[MEASURED];
        TestHttpClient.Reply last = null;
        for (int i = 0; i < MEASURED; i++) {
            long start = System.nanoTime();
            last = round(api, scenario);
            millis[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        double checkouts = (counter.checkouts() - checkoutsBefore) / (double) MEASURED;
        Arrays.sort(millis);
        return new Result(scenario, percentile(millis, 0.50), percentile(millis, 0.95), percentile(millis, 0.99), checkouts,
                last.wireBytes(), last.body().length());
    }

    private static TestHttpClient.Reply round(TestHttpClient api, Scenario scenario) {
        TestHttpClient.Reply last = null;
        for (String path : scenario.paths()) {
            last = api.get(path);
            assertThat(last.status()).as(path).isEqualTo(200);
        }
        return last;
    }

    static double percentile(double[] sorted, double p) {
        return sorted[(int) Math.ceil(p * sorted.length) - 1];
    }

    private static String table(List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Endpoint | p50 ms | p95 ms | p99 ms | connections per call | body bytes | wire bytes |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (Result r : results) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %.1f | %d | %d |%n", r.scenario().name(), format(r.p50()),
                    format(r.p95()), format(r.p99()), r.checkoutsPerRound(), r.bodyBytes(), r.wireBytes()));
        }
        return sb.toString();
    }

    private static String format(double ms) {
        return String.format(Locale.ROOT, "%.2f", ms);
    }

    /** Ids the scenarios need, plus the signed state a real tenant reaches. */
    private static final class Fixture {
        final TestHttpClient api;
        final ObjectMapper json;
        final UUID programId;
        final UUID assessmentId;
        UUID documentId;
        UUID ruleVersionId;
        UUID reviewedMappingId;
        UUID supersedingRuleVersionId;
        UUID ruleSourceId;

        Fixture(TestHttpClient api, ObjectMapper json, UUID programId, UUID assessmentId) {
            this.api = api;
            this.json = json;
            this.programId = programId;
            this.assessmentId = assessmentId;
        }

        /**
         * Reviews and signs every jurisdiction whose mappings are all current. The seed's rule and course
         * changes leave the others stale, so they stay open, as they would for a real tenant.
         */
        void decideAndSignCurrentJurisdictions() {
            int signed = 0;
            for (String jurisdiction : List.of("UT", "AZ", "CA", "TX", "CO")) {
                JsonNode mappings = read(api.get("/api/v1/assessments/" + assessmentId + "/mappings?jurisdiction=" + jurisdiction)).get("mappings");
                boolean anyStale = false;
                for (JsonNode mapping : mappings) {
                    anyStale |= mapping.get("stale").asBoolean();
                    if (documentId == null && !mapping.get("evidence").isNull()) {
                        documentId = UUID.fromString(mapping.get("evidence").get("documentId").asString());
                    }
                    if (ruleVersionId == null) {
                        ruleVersionId = UUID.fromString(mapping.get("ruleVersionId").asString());
                    }
                }
                if (anyStale) {
                    continue;
                }
                boolean first = true;
                for (JsonNode mapping : mappings) {
                    reviewedMappingId = UUID.fromString(mapping.get("id").asString());
                    if (first) {
                        ok(api.postJson("/api/v1/mappings/" + reviewedMappingId + "/reviews",
                                "{\"kind\":\"EVIDENCE_REQUESTED\",\"note\":\"Benchmark fixture request.\"}"));
                        first = false;
                    }
                    boolean found = mapping.get("finding").asString().equals("EVIDENCE_FOUND");
                    ok(api.postJson("/api/v1/mappings/" + mapping.get("id").asString() + "/reviews", found
                            ? "{\"kind\":\"ACCEPTED\"}"
                            : "{\"kind\":\"GAP_CONFIRMED\",\"note\":\"Benchmark fixture decision.\"}"));
                }
                ok(api.postJson("/api/v1/programs/" + programId + "/determinations",
                        "{\"jurisdiction\":\"" + jurisdiction + "\",\"outcome\":\"MEETS\"}"));
                signed++;
            }
            assertThat(signed).as("jurisdictions the fixture could sign").isGreaterThanOrEqualTo(1);
            for (JsonNode version : read(api.get("/api/v1/rule-versions"))) {
                if (!version.get("supersedesId").isNull()) {
                    supersedingRuleVersionId = UUID.fromString(version.get("id").asString());
                }
            }
            assertThat(supersedingRuleVersionId).as("a rule version that supersedes another").isNotNull();
            ruleSourceId = UUID.fromString(read(api.get("/api/v1/rule-sources")).get(0).get("id").asString());
        }

        private JsonNode read(TestHttpClient.Reply reply) {
            ok(reply);
            return json.readTree(reply.body());
        }

        private static void ok(TestHttpClient.Reply reply) {
            assertThat(reply.status()).as(reply.body()).isBetween(200, 299);
        }
    }

}
