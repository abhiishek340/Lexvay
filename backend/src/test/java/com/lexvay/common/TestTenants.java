package com.lexvay.common;

import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.tenant.TenantBootstrapService;
import java.util.UUID;

/** Creates throwaway tenants and users for tests. Every tenant gets a unique name and emails. */
public class TestTenants {

    public static final String PASSWORD = "correct-horse-battery-staple";

    private final TenantBootstrapService bootstrap;

    public TestTenants(TenantBootstrapService bootstrap) {
        this.bootstrap = bootstrap;
    }

    public record TenantWithUsers(UUID tenantId, LexvayUser admin, LexvayUser contributor, LexvayUser reviewer,
                                  LexvayUser signer) {
    }

    public TenantWithUsers create(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var created = bootstrap.createTenant(label + " " + suffix, "admin-" + suffix + "@" + label + ".test",
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
