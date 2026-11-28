package com.lexvay.eval;

import static com.lexvay.eval.LabFixtures.DOC_A;
import static com.lexvay.eval.LabFixtures.key;
import static com.lexvay.eval.LabFixtures.requirement;
import static com.lexvay.eval.LabFixtures.span;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.document.DocumentService;
import com.lexvay.rule.RequirementType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/** The key format, its schema, the split and the holdout lock, against the shipped demo key and throwaway copies of it. */
class AnswerKeysTest {

    static final Path EVAL = Path.of("..", "eval");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir Path dir;
    String demo;

    @BeforeEach
    void copySchemaAndDemoKey() throws IOException {
        Files.createDirectories(dir.resolve("keys"));
        Files.copy(EVAL.resolve("keys").resolve("schema.json"), dir.resolve("keys").resolve("schema.json"));
        demo = Files.readString(EVAL.resolve("keys").resolve("demo-northfield.yml"));
        write("demo-northfield.yml", demo);
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve("keys").resolve(name), content);
    }

    private void lock(String... lines) throws IOException {
        Files.writeString(dir.resolve("holdout.lock"), String.join("\n", lines) + "\n");
    }

    private String hash(String name) throws IOException {
        return DocumentService.sha256(Files.readAllBytes(dir.resolve("keys").resolve(name)));
    }

    private AnswerKeys keys() {
        return new AnswerKeys(JSON, dir);
    }

    @Test
    void theShippedDemoKeyIsValidFictionalAndCoversTheFortyTwoSeededRequirements() {
        List<AnswerKeys.Loaded> dev = new AnswerKeys(JSON, EVAL).dev();

        assertThat(dev).singleElement().satisfies(loaded -> {
            assertThat(loaded.id()).isEqualTo("demo-northfield");
            AnswerKey key = loaded.key();
            assertThat(key.split()).isEqualTo(AnswerKey.Split.dev);
            assertThat(key.provenance().fictional()).isTrue();
            assertThat(key.provenance().labellers()).extracting(AnswerKey.Labeller::independent).containsOnly(false);
            assertThat(key.requirements()).hasSize(42);
            assertThat(key.requirements()).extracting(AnswerKey.Requirement::severity).contains(AnswerKey.Severity.CRITICAL, AnswerKey.Severity.STANDARD);
            assertThat(key.provenance().sourceBundle().documents()).hasSize(13).allSatisfy(d -> assertThat(d.sha256()).hasSize(64));
        });
    }

    @Test
    void aValueOutsideTheSchemaIsRefusedAndNamed() throws IOException {
        write("demo-northfield.yml", demo.replaceFirst("expected: EVIDENCE_FOUND", "expected: CONFIRMED_GAP"));

        assertThatThrownBy(() -> keys().dev()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo-northfield.yml does not match keys/schema.json").hasMessageContaining("expected");
    }

    @Test
    void aFieldTheSchemaDoesNotKnowIsRefused() throws IOException {
        write("demo-northfield.yml", demo.replaceFirst("  fictional: true", "  fictional: true\n  labellerNames: [x]"));

        assertThatThrownBy(() -> keys().dev()).isInstanceOf(IllegalStateException.class).hasMessageContaining("labellerNames");
    }

    @Test
    void aKeyThatDoesNotSayItsSplitIsRefused() throws IOException {
        write("demo-northfield.yml", demo.replaceFirst("(?m)^split: dev\n", ""));

        assertThatThrownBy(() -> keys().dev()).isInstanceOf(IllegalStateException.class).hasMessageContaining("must declare split");
    }

    @Test
    void aSpanWithAnImpossibleRangeIsRefusedAfterTheSchemaPasses() throws IOException {
        write("demo-northfield.yml", demo.replaceFirst("charStart: 187, charEnd: 251", "charStart: 251, charEnd: 187"));

        assertThatThrownBy(() -> keys().dev()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is inconsistent").hasMessageContaining("UT/UT-1 has a span with an impossible range");
    }

    @Test
    void aNumericRequirementWithoutItsNumericRuleIsRefused() throws IOException {
        write("demo-northfield.yml", demo.replaceFirst("(?m)^    numeric: .*\n", ""));

        assertThatThrownBy(() -> keys().dev()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UT/UT-1 is numeric but has no numeric rule");
    }

    @Test
    void consistencyRulesNameWhatIsWrong() {
        var found = AnswerKey.Expected.EVIDENCE_FOUND;
        var missing = AnswerKey.Expected.EVIDENCE_MISSING;
        var standard = AnswerKey.Severity.STANDARD;
        var adjudicated = new AnswerKey.Requirement("R-5", "UT", "t", "x", RequirementType.SUBJECT, null, null, found, standard,
                List.of(span(DOC_A, 1, 0, 10)), null,
                new AnswerKey.Adjudication(List.of(new AnswerKey.Label("A", found), new AnswerKey.Label("Z", found)),
                        AnswerKey.Expected.AMBIGUOUS, "senior reviewer"));
        AnswerKey key = key(AnswerKey.Split.dev, false, true, List.of(
                requirement("UT", "R-1", RequirementType.SUBJECT, found, standard),
                requirement("UT", "R-2", RequirementType.SUBJECT, missing, standard, span(DOC_A, 1, 0, 10)),
                requirement("UT", "R-3", RequirementType.SUBJECT, found, standard, span("0".repeat(64), 1, 0, 10)),
                requirement("UT", "R-3", RequirementType.SUBJECT, found, standard, span(DOC_A, 1, 0, 10)),
                requirement("UT", "R-4", RequirementType.NUMERIC, found, standard, span(DOC_A, 1, 0, 10)),
                adjudicated));

        assertThat(key.problems()).containsExactly(
                "UT/R-1 is EVIDENCE_FOUND but accepts no evidence span",
                "UT/R-2 is EVIDENCE_MISSING but accepts an evidence span",
                "UT/R-3 has a span in a document that is not in the source bundle: " + "0".repeat(64),
                "UT/R-3 appears twice",
                "UT/R-5 is adjudicated to AMBIGUOUS but carries EVIDENCE_FOUND",
                "UT/R-5 is adjudicated but the labellers did not disagree",
                "UT/R-5 names labeller Z, who is not in the provenance");
    }

    @Test
    void aValidAdjudicationByTwoLabellersIsReadFromYaml() throws IOException {
        String adjudicated = demo
                .replaceFirst("(?m)^    - \\{ id: A,.*$", "    - { id: A, role: \"first labeller\", independent: true }\n    - { id: B, role: \"second labeller\", independent: true }")
                .replaceFirst("(?m)^    notes: \"Only COUN 650 covers addictions and it is an elective\\.\"$",
                        "$0\n    adjudication:\n      labels: [{ labeller: A, finding: AMBIGUOUS }, { labeller: B, finding: EVIDENCE_FOUND }]\n      final: AMBIGUOUS\n      adjudicator: \"senior reviewer\"");
        assertThat(adjudicated).contains("adjudication:");
        write("demo-northfield.yml", adjudicated);

        AnswerKey key = keys().dev().get(0).key();

        AnswerKey.Requirement ut8 = key.requirements().stream().filter(r -> r.key().equals("UT/UT-8")).findFirst().orElseThrow();
        assertThat(ut8.adjudication().finalLabel()).isEqualTo(AnswerKey.Expected.AMBIGUOUS);
        assertThat(ut8.adjudication().labels()).extracting(AnswerKey.Label::labeller).containsExactly("A", "B");
        assertThat(key.provenance().labellers()).extracting(AnswerKey.Labeller::independent).containsOnly(true);
    }

    @Test
    void aDevRunReadsNoHoldoutKeyNotEvenOneThatIsMalformed() throws IOException {
        write("held-out.yml", "split: holdout\nnothing: that is a key\n");
        lock(hash("held-out.yml") + "  held-out.yml");

        assertThat(keys().dev()).extracting(AnswerKeys.Loaded::id).containsExactly("demo-northfield");
    }

    @Test
    void aKeyNamedInTheLockIsNeverRunAsDevEvenIfItsSplitWasEditedToDev() throws IOException {
        write("held-out.yml", demo.replaceFirst("(?m)^split: dev", "split: holdout"));
        lock(hash("held-out.yml") + "  held-out.yml");
        write("held-out.yml", demo);

        assertThat(keys().dev()).extracting(AnswerKeys.Loaded::id).containsExactly("demo-northfield");
    }

    @Test
    void aHoldoutRunReadsOnlyHoldoutKeysWhoseHashMatchesTheLock() throws IOException {
        write("held-out.yml", demo.replaceFirst("(?m)^split: dev", "split: holdout"));
        lock("# frozen", "", hash("held-out.yml") + "  held-out.yml");

        List<AnswerKeys.Loaded> held = keys().holdout();

        assertThat(held).singleElement().satisfies(k -> {
            assertThat(k.id()).isEqualTo("held-out");
            assertThat(k.key().split()).isEqualTo(AnswerKey.Split.holdout);
            assertThat(k.sha256()).isEqualTo(hash("held-out.yml"));
        });
    }

    @Test
    void aHoldoutKeyThatChangedAfterItWasLockedIsRefused() throws IOException {
        write("held-out.yml", demo.replaceFirst("(?m)^split: dev", "split: holdout"));
        lock(hash("held-out.yml") + "  held-out.yml");
        write("held-out.yml", Files.readString(dir.resolve("keys").resolve("held-out.yml")).replace("COUN 605", "COUN 606"));

        assertThatThrownBy(() -> keys().holdout()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("held-out.yml differs from holdout.lock").hasMessageContaining("deliberate commit of its own");
    }

    @Test
    void aHoldoutKeyThatIsNotInTheLockIsRefused() throws IOException {
        write("held-out.yml", demo.replaceFirst("(?m)^split: dev", "split: holdout"));

        assertThatThrownBy(() -> keys().holdout()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("held-out.yml declares split holdout but is not in holdout.lock");
    }

    @Test
    void aLockLineForAMissingFileOrForADevKeyIsRefused() throws IOException {
        lock("a".repeat(64) + "  gone.yml", hash("demo-northfield.yml") + "  demo-northfield.yml");

        assertThatThrownBy(() -> keys().holdout()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gone.yml, which does not exist")
                .hasMessageContaining("demo-northfield.yml is in holdout.lock but does not declare split holdout");
    }

    @Test
    void aMalformedOrRepeatedLockLineIsRefused() throws IOException {
        lock("not a hash  held-out.yml");
        assertThatThrownBy(() -> keys().holdout()).isInstanceOf(IllegalStateException.class).hasMessageContaining("not '<sha256>  <key file>'");

        lock("a".repeat(64) + "  held-out.yml", "b".repeat(64) + "  held-out.yml");
        assertThatThrownBy(() -> keys().holdout()).isInstanceOf(IllegalStateException.class).hasMessageContaining("names held-out.yml twice");
    }

    @Test
    void withNoHoldoutKeyAndNoLockTheHoldoutIsEmpty() {
        assertThat(keys().holdout()).isEmpty();
    }

    @Test
    void theShippedLockNamesNoKeyBecauseTheRepositoryHoldsNoHeldOutKey() {
        assertThat(new AnswerKeys(JSON, EVAL).holdout()).isEmpty();
    }
}
