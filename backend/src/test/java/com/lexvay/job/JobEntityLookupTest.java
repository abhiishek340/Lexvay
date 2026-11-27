package com.lexvay.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The job lookups by record: newest job of a type for one record, and the same for many records at once. */
class JobEntityLookupTest extends AbstractDatabaseTest {

    @Autowired JobRepository jobs;
    @Autowired TestTenants tenants;

    /** Enqueues a job and marks it done at once: the queue is shared by the tests of the JVM and these records do not exist. */
    private UUID enqueue(UUID tenantId, JobType type, UUID entityId) {
        return TenantContext.runAs(tenantId, () -> {
            UUID id = jobs.enqueue(type, entityId, Map.of("id", entityId.toString()));
            jobs.markDone(id);
            return id;
        });
    }

    @Test
    void latestIsTheNewestJobOfTheTypeForTheRecordAndExposesItsEntityId() {
        var t = tenants.create("lookup");
        UUID document = UUID.randomUUID();
        UUID first = enqueue(t.tenantId(), JobType.INGEST_DOCUMENT, document);
        UUID second = enqueue(t.tenantId(), JobType.INGEST_DOCUMENT, document);
        enqueue(t.tenantId(), JobType.INGEST_DOCUMENT, UUID.randomUUID());
        enqueue(t.tenantId(), JobType.EXTRACT_REQUIREMENTS, document);

        Job latest = TenantContext.runAs(t.tenantId(), () -> jobs.latest(JobType.INGEST_DOCUMENT, document)).orElseThrow();

        assertThat(latest.id()).isEqualTo(second).isNotEqualTo(first);
        assertThat(latest.entityId()).isEqualTo(document);
        assertThat(latest.type()).isEqualTo(JobType.INGEST_DOCUMENT);
        assertThat(TenantContext.runAs(t.tenantId(), () -> jobs.latest(JobType.RUN_ASSESSMENT, document))).isEmpty();
        assertThat(TenantContext.runAs(t.tenantId(), () -> jobs.latest(JobType.INGEST_DOCUMENT, UUID.randomUUID()))).isEmpty();
    }

    @Test
    void anotherTenantsJobForTheSameRecordIsNotFound() {
        var mine = tenants.create("lookup-a");
        var other = tenants.create("lookup-b");
        UUID record = UUID.randomUUID();
        enqueue(other.tenantId(), JobType.EXTRACT_REQUIREMENTS, record);

        assertThat(TenantContext.runAs(mine.tenantId(), () -> jobs.latest(JobType.EXTRACT_REQUIREMENTS, record))).isEmpty();
        assertThat(TenantContext.runAs(mine.tenantId(),
                () -> jobs.latestByEntity(JobType.EXTRACT_REQUIREMENTS, List.of(record)))).isEmpty();
    }

    @Test
    void latestByEntityReturnsTheNewestJobOfEachRecordAndOmitsRecordsWithoutOne() {
        var t = tenants.create("lookup-many");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID none = UUID.randomUUID();
        enqueue(t.tenantId(), JobType.EXTRACT_REQUIREMENTS, a);
        UUID newestOfA = enqueue(t.tenantId(), JobType.EXTRACT_REQUIREMENTS, a);
        UUID onlyOfB = enqueue(t.tenantId(), JobType.EXTRACT_REQUIREMENTS, b);
        enqueue(t.tenantId(), JobType.INGEST_DOCUMENT, none);

        Map<UUID, Job> latest = TenantContext.runAs(t.tenantId(),
                () -> jobs.latestByEntity(JobType.EXTRACT_REQUIREMENTS, List.of(a, b, none)));

        assertThat(latest).containsOnlyKeys(a, b);
        assertThat(latest.get(a).id()).isEqualTo(newestOfA);
        assertThat(latest.get(b).id()).isEqualTo(onlyOfB);
        assertThat(jobs.latestByEntity(JobType.EXTRACT_REQUIREMENTS, List.of())).isEmpty();
    }
}
