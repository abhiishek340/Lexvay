package com.lexvay.eval;

import com.lexvay.common.YamlFile;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** `make eval`: run the evaluation, print the table, exit non-zero when a limit is broken. */
@Component
@Profile("eval")
class EvalRunner implements ApplicationRunner {

    private final EvalService service;
    private final ConfigurableApplicationContext context;
    private final Path seedDir;
    private final Path evalDir;

    EvalRunner(EvalService service, ConfigurableApplicationContext context, @Value("${lexvay.seed.dir:../seed}") String seedDir,
               @Value("${lexvay.eval.dir:../eval}") String evalDir) {
        this.service = service;
        this.context = context;
        this.seedDir = Path.of(seedDir);
        this.evalDir = Path.of(evalDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        int code;
        try {
            EvalReport report = service.run(seedDir, YamlFile.read(evalDir.resolve("golden.yml"), Golden.class),
                    YamlFile.read(evalDir.resolve("thresholds.yml"), Thresholds.class));
            System.out.print(report.render());
            code = report.passed() ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.println("eval failed: " + e.getMessage());
            code = 2;
        }
        int exitCode = code;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> exitCode));
    }
}
