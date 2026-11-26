package com.lexvay.perf;

import com.lexvay.seed.SeedLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Fills the database with more tenants than the demo has: the fictional seed loaded again and again under
 * other tenant names and e-mail addresses, so planner statistics and row-level security see realistic volume.
 */
final class PerfSeed {

    private PerfSeed() {
    }

    /** Loads {@code copies} renamed copies of the seed through the real services (about a second each). */
    static void loadCopies(SeedLoader loader, Path seedDir, String password, int copies) {
        Path work = createTempDir();
        try {
            String manifest = Files.readString(seedDir.resolve("manifest.yml"), StandardCharsets.UTF_8);
            for (String shared : List.of("pdf", "rules", "fake-llm.yml")) {
                Files.createSymbolicLink(work.resolve(shared), seedDir.toAbsolutePath().resolve(shared));
            }
            IntStream.rangeClosed(1, copies).forEach(n -> {
                String renamed = manifest
                        .replace("name: Northfield University", "name: Northfield University " + n)
                        .replace("name: Westbrook College", "name: Westbrook College " + n)
                        .replace("@northfield.example", "." + n + "@northfield.example")
                        .replace("@westbrook.example", "." + n + "@westbrook.example");
                try {
                    Files.writeString(work.resolve("manifest.yml"), renamed, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                loader.load(work, password);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("lexvay-perf-seed-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
