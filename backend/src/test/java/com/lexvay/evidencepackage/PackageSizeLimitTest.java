package com.lexvay.evidencepackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.audit.AuditEntry;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.document.FileStore;
import com.lexvay.review.ReviewFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** A package over {@code lexvay.package.max-bytes} fails with a clear message and leaves no file, stored or temporary. */
@TestPropertySource(properties = "lexvay.package.max-bytes=1500")
class PackageSizeLimitTest extends AbstractAssessmentTest {

    @Autowired FileStore files;
    @Autowired AuditRepository audit;

    @Test
    void anOversizedPackageFailsWholeAndLeavesNothingBehind() throws IOException {
        var rf = new ReviewFixtures(mvc, json, fx);
        var scenario = rf.californiaScenario(t, "size-limit");
        rf.decideAll(t, scenario);
        UUID determinationId = UUID.fromString(fx.read(rf.sign(t.signer(), scenario.programId(), "CA", "DOES_NOT_MEET")).get("id").asString());
        var packages = new PackageFixtures(mvc, json, runner);
        packages.readyReport(t.signer(), determinationId);
        UUID packageId = packages.acceptedPackage(t.signer(), determinationId);
        long tempBefore = temporaryPackageFiles();

        runner.runPending();

        JsonNode failed = packages.status(t.signer(), packageId);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("sizeBytes").isNull()).isTrue();
        assertThat(failed.get("sha256").isNull()).isTrue();
        assertThat(failed.get("error").asString()).contains("larger than 1500 bytes").contains("lexvay.package.max-bytes");
        assertThat(files.exists(t.tenantId() + "/package-" + packageId)).as("no partial ZIP is stored").isFalse();
        assertThat(temporaryPackageFiles()).as("the temporary file is deleted").isEqualTo(tempBefore);
        assertThat(packages.file(t.signer(), packageId)).hasStatus(409);
        List<AuditEntry> trail = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(packageId));
        assertThat(trail).extracting(AuditEntry::action).containsExactly("PACKAGE_REQUESTED");
    }

    private static long temporaryPackageFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("lexvay-package-")).count();
        }
    }
}
