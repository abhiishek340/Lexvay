package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestHttpClient;
import com.lexvay.common.TestTenants;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The export does not hold the log (D-377). 20 000 entries of about 3 KB are 63 MB of CSV; a writer that collected them
 * would keep at least that much live while the response is produced. The heap is measured as live data (after a
 * collection) before the request and, repeatedly, while it is served, and the largest difference must stay under 32 MB.
 */
class AuditExportMemoryTest extends AbstractDatabaseTest {

    static final int ROWS = 20_000;
    static final long BUDGET_BYTES = 32L * 1024 * 1024;

    @LocalServerPort int port;
    @Autowired TestTenants tenants;
    @Autowired JdbcClient jdbc;
    @Autowired AuditRepository repository;

    private static long liveHeap() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    @Test
    void twentyThousandEntriesAreStreamedWithoutHoldingThem() throws Exception {
        var t = tenants.create("auditmemory");
        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("""
                INSERT INTO audit_log (tenant_id, user_id, action, entity_type, entity_id, detail, created_by)
                SELECT :tenant, :user, 'BULK_ENTRY', 'bulk', gen_random_uuid(),
                       jsonb_build_object('n', n, 'filler', repeat(md5(n::text), 100)), :user
                FROM generate_series(1, :rows) AS n
                """).param("tenant", t.tenantId()).param("user", t.admin().id()).param("rows", ROWS).update());

        TestHttpClient client = new TestHttpClient(port);
        client.login(t.admin().email(), TestTenants.PASSWORD);

        long baseline = liveHeap();
        var peak = new AtomicLong(baseline);
        var running = new AtomicBoolean(true);
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (running.get()) {
                peak.accumulateAndGet(liveHeap(), Math::max);
                try {
                    Thread.sleep(150); // let the server run between collections
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        long bytes = 0;
        long records = 0;
        try {
            var response = client.stream("/api/v1/audit.csv?action=BULK_ENTRY");
            assertThat(response.statusCode()).isEqualTo(200);
            try (InputStream in = response.body()) {
                byte[] buffer = new byte[64 * 1024];
                byte previous = 0;
                for (int read; (read = in.read(buffer)) != -1; ) {
                    bytes += read;
                    for (int i = 0; i < read; i++) {
                        if (buffer[i] == '\n' && previous == '\r') {
                            records++;
                        }
                        previous = buffer[i];
                    }
                }
            }
        } finally {
            running.set(false);
            sampler.join();
        }

        long growth = peak.get() - baseline;
        System.out.printf("Audit export: %d records, %d MB; live heap before %d MB, highest while serving %d MB, growth %d MB%n",
                records, bytes >> 20, baseline >> 20, peak.get() >> 20, growth >> 20);
        assertThat(records).as("the demo notice, the header and the entries").isEqualTo(ROWS + 2);
        assertThat(bytes).as("the file really is larger than the budget").isGreaterThan(BUDGET_BYTES);
        assertThat(growth).as("live heap growth while streaming").isLessThan(BUDGET_BYTES);

        var exports = TenantContext.runAs(t.tenantId(), () -> repository.newestFirst(
                new AuditFilter("AUDIT_EXPORTED", null, null, null, null, null), 10, 0));
        assertThat(exports).hasSize(1);
        assertThat(exports.get(0).detail()).containsEntry("rowCount", ROWS).containsEntry("complete", true);
    }
}
