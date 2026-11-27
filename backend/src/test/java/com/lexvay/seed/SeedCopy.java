package com.lexvay.seed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The real seed's Northfield tenant under another name and email domain, in a temporary directory that links the
 * shared rule texts, PDFs and model scripts. A test that loads it owns its tenant, whatever else ran before.
 */
public final class SeedCopy {

    private SeedCopy() {
    }

    public static Path northfield(String tenantName, String emailDomain) throws IOException {
        Path seed = Path.of("..", "seed").toAbsolutePath().normalize();
        Path copy = Files.createTempDirectory("lexvay-seed-");
        for (String shared : List.of("fake-llm.yml", "rules", "pdf")) {
            Files.createSymbolicLink(copy.resolve(shared), seed.resolve(shared));
        }
        String manifest = Files.readString(seed.resolve("manifest.yml"));
        manifest = manifest.substring(0, manifest.indexOf("  - name: Westbrook College"))
                .replace("northfield.example", emailDomain).replace("Northfield University", tenantName);
        Files.writeString(copy.resolve("manifest.yml"), manifest);
        return copy;
    }
}
