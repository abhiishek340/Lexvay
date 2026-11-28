package com.lexvay.eval;

import com.lexvay.common.YamlFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * `make eval`: run the pipeline evaluation and the retrieval measurement, print both tables, write docs/RETRIEVAL.md, and
 * exit non-zero when a limit of either is broken. A run against a local model also keeps its report under
 * eval/runs/ (gitignored), named by date and model, for the founder to keep (D-618).
 *
 * <p>With {@code lexvay.eval.lab} set to {@code dev} or {@code holdout} it runs the evaluation lab instead (`make eval-lab`,
 * `make eval-holdout`, D-621): the keys of that split are scored, the report is printed and written to eval/reports/ as
 * Markdown and JSON (gitignored), and {@code lexvay.eval.baseline} adds the generic-model comparison.
 */
@Component
@Profile("eval")
class EvalRunner implements ApplicationRunner {

    private final EvalService service;
    private final RetrievalService retrieval;
    private final LabService labs;
    private final ObjectMapper json;
    private final ConfigurableApplicationContext context;
    private final Path seedDir;
    private final Path evalDir;
    private final Path retrievalReport;
    private final String lab;
    private final boolean baseline;

    EvalRunner(EvalService service, RetrievalService retrieval, LabService labs, ObjectMapper json, ConfigurableApplicationContext context,
               @Value("${lexvay.seed.dir:../seed}") String seedDir, @Value("${lexvay.eval.dir:../eval}") String evalDir,
               @Value("${lexvay.eval.retrieval-report:../docs/RETRIEVAL.md}") String retrievalReport,
               @Value("${lexvay.eval.lab:}") String lab, @Value("${lexvay.eval.baseline:false}") boolean baseline) {
        this.service = service;
        this.retrieval = retrieval;
        this.labs = labs;
        this.json = json;
        this.context = context;
        this.seedDir = Path.of(seedDir);
        this.evalDir = Path.of(evalDir);
        this.retrievalReport = Path.of(retrievalReport);
        this.lab = lab;
        this.baseline = baseline;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code;
        try {
            code = lab.isEmpty() ? runPipelineAndRetrieval() : runLab(AnswerKey.Split.valueOf(lab));
        } catch (RuntimeException e) {
            System.err.println("eval failed: " + e.getMessage());
            code = 2;
        }
        int exitCode = code;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> exitCode));
    }

    private int runPipelineAndRetrieval() {
        Thresholds thresholds = YamlFile.read(evalDir.resolve("thresholds.yml"), Thresholds.class);
        EvalReport report = service.run(seedDir, YamlFile.read(evalDir.resolve("golden.yml"), Golden.class), thresholds);
        RetrievalReport measured = retrieval.run(seedDir, evalDir.resolve("retrieval"),
                YamlFile.read(evalDir.resolve("retrieval").resolve("queries.yml"), RetrievalQueries.class), thresholds.retrieval());
        System.out.print(report.render());
        System.out.println();
        System.out.print(measured.render());
        write(retrievalReport, measured.markdown());
        if (report.mode() == EvalReport.Mode.LOCAL_LIVE) {
            Path kept = evalDir.resolve("runs").resolve(report.fileName());
            write(kept, report.render());
            System.out.println("Report kept in " + kept);
        }
        return report.passed() && measured.passed() ? 0 : 1;
    }

    private int runLab(AnswerKey.Split split) {
        AnswerKeys keys = new AnswerKeys(json, evalDir);
        List<AnswerKeys.Loaded> selected = split == AnswerKey.Split.holdout ? keys.holdout() : keys.dev();
        if (selected.isEmpty() && split == AnswerKey.Split.holdout) {
            System.out.println("eval-holdout: holdout.lock names no key, so there is nothing to run. Nothing was run.");
            return 0;
        }
        LabThresholds thresholds = YamlFile.read(evalDir.resolve("lab-thresholds.yml"), LabThresholds.class);
        LabReport report = labs.run(seedDir, split, selected, thresholds, baseline);
        Path reports = evalDir.resolve("reports");
        write(reports.resolve(report.fileStem() + ".md"), report.markdown());
        write(reports.resolve(report.fileStem() + ".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(report.json(json)) + "\n");
        System.out.print(report.markdown());
        System.out.println("\nReports kept in " + reports.resolve(report.fileStem() + ".{md,json}"));
        return report.passed() ? 0 : 1;
    }

    private static void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
