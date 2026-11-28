package com.lexvay.common;

import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantBootstrapService;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Creates throwaway tenants and users for tests. Every tenant gets a unique name and emails. */
public class TestTenants {

    public static final String PASSWORD = "correct-horse-battery-staple";

    private final TenantBootstrapService bootstrap;
    private final JdbcClient jdbc;

    public TestTenants(TenantBootstrapService bootstrap, JdbcClient jdbc) {
        this.bootstrap = bootstrap;
        this.jdbc = jdbc;
    }

    /** What the database administrator does (docs/OPERATIONS.md); no API changes a tenant's mode. */
    public void setDataMode(UUID tenantId, DataMode dataMode) {
        TenantContext.runAs(tenantId, () -> jdbc.sql("UPDATE tenant SET data_mode = :mode WHERE id = :id")
                .param("mode", dataMode.name()).param("id", tenantId).update());
    }

    public record TenantWithUsers(UUID tenantId, LexvayUser admin, LexvayUser contributor, LexvayUser reviewer,
                                  LexvayUser signer) {
    }

    /** A DEMO tenant: tests answer with the scripted model, which only DEMO tenants may use. */
    public TenantWithUsers create(String label) {
        return create(label, DataMode.DEMO);
    }

    public TenantWithUsers create(String label, DataMode dataMode) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var created = bootstrap.createTenant(label + " " + suffix, dataMode, "admin-" + suffix + "@" + label + ".test",
                "Admin " + label, PASSWORD);
        UUID tenantId = created.tenantId();
        UUID actor = created.admin().id();
        var contributor = bootstrap.createUser(tenantId, actor, "contributor-" + suffix + "@" + label + ".test",
                "Contributor " + label, Role.CONTRIBUTOR, PASSWORD);
        var reviewer = bootstrap.createUser(tenantId, actor, "reviewer-" + suffix + "@" + label + ".test",
                "Reviewer " + label, Role.REVIEWER, PASSWORD);
        var signer = bootstrap.createUser(tenantId, actor, "signer-" + suffix + "@" + label + ".test",
                "Signer " + label, Role.SIGNER, PASSWORD);
        return new TenantWithUsers(tenantId, created.admin(), contributor, reviewer, signer);
    }
}
