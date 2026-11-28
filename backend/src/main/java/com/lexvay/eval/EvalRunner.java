package com.lexvay.eval;

import com.lexvay.common.YamlFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * `make eval`: run the pipeline evaluation and the retrieval measurement, print both tables, write docs/RETRIEVAL.md, and
 * exit non-zero when a limit of either is broken. A run against a local model also keeps its report under
 * eval/runs/ (gitignored), named by date and model, for the founder to keep (D-618).
 */
@Component
@Profile("eval")
class EvalRunner implements ApplicationRunner {

    private final EvalService service;
    private final RetrievalService retrieval;
    private final ConfigurableApplicationContext context;
    private final Path seedDir;
    private final Path evalDir;
    private final Path retrievalReport;

    EvalRunner(EvalService service, RetrievalService retrieval, ConfigurableApplicationContext context,
               @Value("${lexvay.seed.dir:../seed}") String seedDir, @Value("${lexvay.eval.dir:../eval}") String evalDir,
               @Value("${lexvay.eval.retrieval-report:../docs/RETRIEVAL.md}") String retrievalReport) {
        this.service = service;
        this.retrieval = retrieval;
        this.context = context;
        this.seedDir = Path.of(seedDir);
        this.evalDir = Path.of(evalDir);
        this.retrievalReport = Path.of(retrievalReport);
    }

    @Override
    public void run(ApplicationArguments args) {
        int code;
        try {
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
            code =report.passed() && measured.passed() ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.println("eval failed: " + e.getMessage());
            code = 2;
        }
        int exitCode = code;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> exitCode));
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
