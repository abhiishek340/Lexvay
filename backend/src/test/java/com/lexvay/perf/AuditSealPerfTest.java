package com.lexvay.perf;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditSealRepository;
import com.lexvay.audit.AuditSealer;
import com.lexvay.audit.AuditVerification;
import com.lexvay.audit.AuditVerificationService;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * SEC-3 budget: verifying 20 000 sealed audit entries takes under 2 seconds, and the sealer's statements on the queue
 * use its indexes. The entries are the perf seed's audit volume, queued as the application's write path would have
 * queued them, then sealed by the real sealer.
 */
@Tag("perf")
class AuditSealPerfTest extends AbstractDatabaseTest {

    static final int ENTRIES = 20_000;
    static final long VERIFY_BUDGET_MS = 2_000;
    static final int RUNS = 5;

    @Autowired TestTenants tenants;
    @Autowired AuditSealer sealer;
    @Autowired AuditVerificationService verification;
    @Autowired JdbcClient jdbc;

    @Test
    void verifyingTwentyThousandSealedEntriesTakesUnderTwoSecondsAndTheQueueUsesItsIndexes() throws Exception {
        UUID tenantId = tenants.create("sealperf").tenantId();
        try (Connection admin = DriverManager.getConnection(jdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            PerfSeed.addAuditVolume(statement, tenantId, 0, ENTRIES);
            PerfSeed.queueAuditVolume(statement);
            statement.execute("ANALYZE audit_log, audit_unsealed");
        }
        List<String> queuePlan = explain(tenantId, AuditSealRepository.QUEUED);

        List<Double> batchMillis = new ArrayList<>();
        long sealStart = System.nanoTime();
        assertThat(sealBatchTimed(tenantId, batchMillis)).isEqualTo(AuditSealer.BATCH_SIZE);
        List<String> dequeuePlan = explain(tenantId, AuditSealRepository.DEQUEUE);
        long sealed = AuditSealer.BATCH_SIZE;
        for (int batch; (batch = sealBatchTimed(tenantId, batchMillis)) > 0; ) {
            sealed += batch;
        }
        long sealMillis = (System.nanoTime() - sealStart) / 1_000_000;
        assertThat(sealed).isGreaterThanOrEqualTo(ENTRIES);

        verify(tenantId); // warm-up
        long[] millis = new long[RUNS];
        AuditVerification result = null;
        for (int run = 0; run < RUNS; run++) {
            long start = System.nanoTime();
            result = verify(tenantId);
            millis[run] = (System.nanoTime() - start) / 1_000_000;
        }
        long[] sorted = millis.clone();
        Arrays.sort(sorted);
        System.out.printf("Audit seal: %d entries sealed in %d ms (%d per batch); verify %s ms, median %d ms, budget %d ms%n",
                sealed, sealMillis, AuditSealer.BATCH_SIZE, Arrays.toString(millis), sorted[RUNS / 2], VERIFY_BUDGET_MS);
        System.out.println("Queue read plan:\n" + String.join("\n", queuePlan));
        System.out.println("Dequeue plan:\n" + String.join("\n", dequeuePlan));
        writeReport(sealed, sealMillis, batchMillis, millis);

        assertThat(result.intact()).isTrue();
        assertThat(result.sealed()).isEqualTo(sealed);
        assertThat(sorted[RUNS / 2]).as("median verify time in ms over %d sealed entries", sealed).isLessThan(VERIFY_BUDGET_MS);
        assertThat(sorted[RUNS - 1]).as("slowest verify time in ms").isLessThan(VERIFY_BUDGET_MS);
        assertThat(queuePlan).as("the queue is read through its tenant index, never scanned")
                .anyMatch(line -> line.contains("audit_unsealed_tenant_idx"))
                .noneMatch(line -> line.contains("Seq Scan on audit_unsealed"));
        assertThat(queuePlan).as("each queued entry is fetched by primary key").anyMatch(line -> line.contains("audit_log_pkey"));
        assertThat(dequeuePlan).as("dequeueing finds the queue rows by primary key, not by scanning the queue")
                .anyMatch(line -> line.contains("audit_unsealed_pkey"))
                .noneMatch(line -> line.contains("Seq Scan on audit_unsealed"));
    }

    private int sealBatchTimed(UUID tenantId, List<Double> batchMillis) {
        long start = System.nanoTime();
        int sealed = sealer.sealBatch(tenantId);
        if (sealed > 0) {
            batchMillis.add((System.nanoTime() - start) / 1_000_000.0);
        }
        return sealed;
    }

    /** The CI summary's numbers (the perf job publishes every file under target/perf), written before the assertions so a failing run still reports. */
    private static void writeReport(long sealed, long sealMillis, List<Double> batchMillis, long[] verifyMillis) throws IOException {
        double[] batches = batchMillis.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double[] verifies = Arrays.stream(verifyMillis).asDoubleStream().sorted().toArray();
        String report = String.format(Locale.ROOT, """
                | Audit seal (%d entries) | Runs | Median ms | p95 ms |
                |---|---:|---:|---:|
                | Sealing one batch of %d | %d | %.2f | %.2f |
                | Verifying all entries (budget %d ms) | %d | %.2f | %.2f |

                Sealing all %d entries took %d ms.
                """, sealed, AuditSealer.BATCH_SIZE, batches.length, EndpointLatencyTest.percentile(batches, 0.5),
                EndpointLatencyTest.percentile(batches, 0.95), VERIFY_BUDGET_MS, verifies.length,
                EndpointLatencyTest.percentile(verifies, 0.5), EndpointLatencyTest.percentile(verifies, 0.95), sealed, sealMillis);
        System.out.println(report);
        Path out = Path.of("target", "perf", "audit-seal.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);
    }

    private AuditVerification verify(UUID tenantId) {
        return TenantContext.runAs(tenantId, verification::verify);
    }

    /** The plan of a sealer statement as the application role runs it, with literals in place of its parameters. */
    private List<String> explain(UUID tenantId, String sql) {
        String literal = sql.replace(":tenantId", "'" + tenantId + "'").replace(":limit", String.valueOf(AuditSealer.BATCH_SIZE))
                .replace(":seq", "0");
        return new ArrayList<>(TenantContext.runAs(tenantId, () -> jdbc.sql("EXPLAIN " + literal).query(String.class).list()));
    }
}
