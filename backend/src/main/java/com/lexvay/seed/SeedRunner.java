package com.lexvay.seed;

import com.lexvay.llm.LlmClient;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** `make seed`: load the demo tenants and exit. */
@Component
@Profile("seed")
public class SeedRunner implements ApplicationRunner {

    private final SeedLoader loader;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final ConfigurableApplicationContext context;
    private final String seedDir;
    private final String demoPassword;

    public SeedRunner(SeedLoader loader, LlmClient llm, ObjectMapper json, ConfigurableApplicationContext context,
                      @Value("${lexvay.seed.dir:../seed}") String seedDir,
                      @Value("${lexvay.seed.demo-password:lexvay-demo}") String demoPassword) {
        this.loader = loader;
        this.llm = llm;
        this.json = json;
        this.context = context;
        this.seedDir = seedDir;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            ModelStrategy model = ScriptedFakeModel.load(Path.of(seedDir), llm, json);
            for (SeedLoader.Result r : loader.load(Path.of(seedDir), demoPassword, model)) {
                System.out.println(r.skipped()
                        ? "seed: " + r.tenant() + " already present, skipped"
                        : "seed: " + r.tenant() + " loaded (program " + r.programId() + ", " + r.documents() + " documents, "
                        + r.requirements() + " requirements, assessment " + r.assessmentId() + ")");
            }
            System.out.println("seed: demo users sign in with the LEXVAY_DEMO_PASSWORD value (default lexvay-demo)");
        } catch (RuntimeException e) {
            System.err.println("seed failed: " + e.getMessage());
            code = 1;
        }
        int exitCode = code;
        System.exit(SpringApplication.exit(context, (ExitCodeGenerator) () -> exitCode));
    }
}
