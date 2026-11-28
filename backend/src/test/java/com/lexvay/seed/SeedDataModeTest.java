package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** MODE-1: the seed is fictional, so it creates DEMO tenants and never writes into one that holds real data. */
class SeedDataModeTest extends AbstractDatabaseTest {

    private static final Path SEED = Path.of("..", "seed");

    @Autowired SeedLoader loader;
    @Autowired TenantRepository tenantRows;
    @Autowired TestTenants tenants;

    /** A seed tenant with a program and nothing else, so the case needs no documents or model. */
    private static SeedManifest.Tenant tenant(String suffix) {
        return new SeedManifest.Tenant("Seed mode test " + suffix, true,
                List.of(new SeedManifest.User("admin-" + suffix + "@seed-mode.example", "Seed Admin", "Administrator", "ADMIN")),
                new SeedManifest.Program("M.S. Counseling", "CLINICAL_MENTAL_HEALTH_COUNSELING", "2026-27", List.of("UT")),
                null, null, null, null, null, null);
    }

    private DataMode modeOf(UUID tenantId) {
        return TenantContext.runAs(tenantId, () -> tenantRows.currentDataMode());
    }

    @Test
    void theSeedCreatesItsTenantsAsDemo() {
        SeedLoader.Result result = loader.loadTenant(tenant(UUID.randomUUID().toString().substring(0, 8)), SEED, "demo-pass", ModelStrategy.NONE);

        assertThat(result.skipped()).isFalse();
        assertThat(modeOf(result.tenantId())).isEqualTo(DataMode.DEMO);
    }

    @Test
    void theEvaluationCorpusIsDemoToo() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        SeedLoader.Corpus corpus = loader.loadCorpus("Corpus mode test " + suffix, "corpus-" + suffix + "@seed-mode.example", "demo-pass",
                tenant(suffix).program(), Map.of());

        assertThat(modeOf(corpus.tenantId())).isEqualTo(DataMode.DEMO);
    }

    @Test
    void aTenantThatHoldsRealDataIsNeverWrittenIntoNorQuietlySkipped() {
        var spec = tenant(UUID.randomUUID().toString().substring(0, 8));
        SeedLoader.Result first = loader.loadTenant(spec, SEED, "demo-pass", ModelStrategy.NONE);
        tenants.setDataMode(first.tenantId(), DataMode.LIVE);

        assertThatThrownBy(() -> loader.loadTenant(spec, SEED, "demo-pass", ModelStrategy.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(spec.name()).hasMessageContaining("holds real data");

        tenants.setDataMode(first.tenantId(), DataMode.DEMO);
        assertThat(loader.loadTenant(spec, SEED, "demo-pass", ModelStrategy.NONE).skipped()).as("a DEMO tenant is skipped as before").isTrue();
    }
}
