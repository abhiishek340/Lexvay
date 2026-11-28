package com.lexvay.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

/** MODE-1: the tenant's data mode, where it comes from and what changing it leaves behind. */
class DataModeTest extends AbstractDatabaseTest {

    @Autowired TestTenants tenants;
    @Autowired TenantRepository tenantRows;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;

    private boolean sessionDemo(TestTenants.TenantWithUsers t) throws Exception {
        var result = mvc.get().uri("/api/v1/auth/session").with(user(t.signer())).exchange();
        return json.readTree(result.getResponse().getContentAsString()).get("demo").asBoolean();
    }

    private List<Map<String, Object>> modeChanges(UUID tenantId) {
        return TenantContext.runAs(tenantId, () -> jdbc.sql("""
                SELECT user_id, entity_type, entity_id, detail ->> 'from' AS from_mode, detail ->> 'to' AS to_mode,
                       detail ->> 'databaseRole' AS database_role
                FROM audit_log WHERE action = 'TENANT_DATA_MODE_CHANGED' ORDER BY created_at
                """).query().listOfRows());
    }

    @Test
    void theSessionSaysDemoForADemoTenantAndNotForATenantWithRealData() throws Exception {
        var demo = tenants.create("mode-demo", DataMode.DEMO);
        var live = tenants.create("mode-live", DataMode.LIVE);

        assertThat(sessionDemo(demo)).isTrue();
        assertThat(sessionDemo(live)).isFalse();
        assertThat(TenantContext.runAs(demo.tenantId(), () -> tenantRows.findById(demo.tenantId())).orElseThrow().dataMode()).isEqualTo(DataMode.DEMO);
        assertThat(TenantContext.runAs(live.tenantId(), () -> tenantRows.currentDataMode())).isEqualTo(DataMode.LIVE);
    }

    @Test
    void theSessionOfNobodyIsNotDemo() throws Exception {
        var result = mvc.get().uri("/api/v1/auth/session").exchange();

        var body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("authenticated").asBoolean()).isFalse();
        assertThat(body.get("demo").asBoolean()).isFalse();
    }

    @Test
    void aTenantInsertedWithoutAModeIsLive() {
        UUID id = UUID.randomUUID();

        String mode = TenantContext.runAs(id, () -> {
            jdbc.sql("INSERT INTO tenant (id, tenant_id, name) VALUES (:id, :id, 'No mode given')").param("id", id).update();
            return jdbc.sql("SELECT data_mode FROM tenant WHERE id = :id").param("id", id).query(String.class).single();
        });

        assertThat(mode).isEqualTo("LIVE");
    }

    @Test
    void onlyDemoAndLiveAreStored() {
        var t = tenants.create("mode-check");

        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () ->
                jdbc.sql("UPDATE tenant SET data_mode = 'REAL' WHERE id = :id").param("id", t.tenantId()).update()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void aChangeOfModeIsAuditedByTheDatabaseAndAnUnchangedModeIsNot() {
        var t = tenants.create("mode-audit", DataMode.DEMO);

        tenants.setDataMode(t.tenantId(), DataMode.DEMO);
        assertThat(modeChanges(t.tenantId())).as("DEMO to DEMO changes nothing").isEmpty();

        tenants.setDataMode(t.tenantId(), DataMode.LIVE);
        tenants.setDataMode(t.tenantId(), DataMode.DEMO);

        assertThat(modeChanges(t.tenantId())).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("user_id")).as("the database acts, not a person of the tenant").isNull();
            assertThat(row.get("entity_type")).isEqualTo("tenant");
            assertThat(row.get("entity_id")).isEqualTo(t.tenantId());
            assertThat(row.get("database_role")).isEqualTo(APP_USER);
        });
        assertThat(modeChanges(t.tenantId())).extracting(r -> r.get("from_mode") + ">" + r.get("to_mode")).containsExactly("DEMO>LIVE", "LIVE>DEMO");
    }

    @Test
    void aTenantCannotChangeAnotherTenantsMode() {
        var mine = tenants.create("mode-mine", DataMode.DEMO);
        var other = tenants.create("mode-other", DataMode.DEMO);

        int changed = TenantContext.runAs(mine.tenantId(), () ->
                jdbc.sql("UPDATE tenant SET data_mode = 'LIVE' WHERE id = :id").param("id", other.tenantId()).update());

        assertThat(changed).isZero();
        assertThat(TenantContext.runAs(other.tenantId(), () -> tenantRows.currentDataMode())).isEqualTo(DataMode.DEMO);
    }
}
