package com.lexvay.eval;

import static com.lexvay.eval.LabFixtures.eightRows;
import static com.lexvay.eval.LabFixtures.overall;
import static com.lexvay.eval.LabFixtures.thresholds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.YamlFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pre-agreed limits: `unset` neither passes nor fails, a set limit does both, and the file must say something about every metric. */
class LabThresholdsTest {

    static final Path FILE = Path.of("..", "eval", "lab-thresholds.yml");

    @TempDir Path dir;

    private LabThresholds read(String yaml) throws IOException {
        Path file = dir.resolve("lab-thresholds.yml");
        Files.writeString(file, yaml);
        return YamlFile.read(file, LabThresholds.class);
    }

    @Test
    void theShippedLimitsAreZeroForSevereFalseAssuranceAndUnsetForEverythingNobodyHasMeasured() {
        LabThresholds shipped = YamlFile.read(FILE, LabThresholds.class);

        assertThat(shipped.limit(LabMetric.SEVERE_FALSE_ASSURANCE).value()).isZero();
        for (LabMetric metric : LabMetric.values()) {
            if (metric != LabMetric.SEVERE_FALSE_ASSURANCE) {
                assertThat(shipped.limit(metric).isSet()).as(metric.key()).isFalse();
                assertThat(shipped.describe(metric)).isEqualTo("unset");
            }
        }
    }

    @Test
    void anUnsetLimitNeitherPassesNorFailsEvenWhenTheMetricIsTerrible() {
        LabThresholds none = LabFixtures.unsetThresholds();

        assertThat(none.breaches(overall(eightRows("k")))).isEmpty();
        assertThat(none.status(LabMetric.SEVERE_FALSE_ASSURANCE, new Ratio(8, 8))).isEqualTo(LabThresholds.Status.UNSET);
    }

    @Test
    void aMaximumIsBrokenByAHigherRateAndKeptByAnEqualOne() {
        LabThresholds limits = thresholds(0.375);

        assertThat(limits.status(LabMetric.SEVERE_FALSE_ASSURANCE, new Ratio(3, 8))).as("3 / 8 = 37.5 %").isEqualTo(LabThresholds.Status.OK);
        assertThat(limits.breaches(overall(eightRows("k")))).isEmpty();
        assertThat(thresholds(0.0).breaches(overall(eightRows("k")))).singleElement().asString()
                .contains("severe false assurance 37.5 % (3 / 8) breaks the limit <= 0.0 %");
    }

    @Test
    void aMinimumIsBrokenByALowerRateAndByNothingToMeasure() throws IOException {
        LabThresholds limits = read("""
                limits:
                  severeFalseAssurance: unset
                  findingAccuracy: 0.9
                  abstentionRate: unset
                  evidencePrecision: unset
                  evidenceRecall: unset
                  numericAccuracy: 1.0
                """);
        Map<LabMetric, Ratio> metrics = new EnumMap<>(overall(eightRows("k")));
        metrics.put(LabMetric.NUMERIC_ACCURACY, new Ratio(0, 0));

        assertThat(limits.breaches(metrics)).containsExactly("finding accuracy 57.1 % (4 / 7) breaks the limit >= 90.0 %",
                "numeric accuracy cannot be measured: the key has nothing to compare");
    }

    @Test
    void aLimitMustBeARateBetweenZeroAndOneOrTheWordUnset() {
        for (String bad : new String[] {"banana", "1.5", "-0.1", "true"}) {
            assertThatThrownBy(() -> read("limits:\n  severeFalseAssurance: " + bad + "\n  findingAccuracy: unset\n  abstentionRate: unset\n"
                    + "  evidencePrecision: unset\n  evidenceRecall: unset\n  numericAccuracy: unset\n")).as(bad).isNotNull();
        }
    }

    @Test
    void theFileMustGiveEveryMetricExactlyOneLimit() {
        assertThatThrownBy(() -> read("limits:\n  severeFalseAssurance: 0.0\n")).hasMessageContaining("exactly one limit");
        assertThatThrownBy(() -> read("limits:\n  severeFalseAssurance: 0.0\n  findingAccuracy: unset\n  abstentionRate: unset\n"
                + "  evidencePrecision: unset\n  evidenceRecall: unset\n  numericAccuracy: unset\n  invented: unset\n")).hasMessageContaining("exactly one limit");
    }
}
