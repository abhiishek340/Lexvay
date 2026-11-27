package com.lexvay.tenant;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.LexvayUser;
import com.lexvay.auth.Role;
import com.lexvay.common.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates tenants and users. A tenant can only be inserted while its own id is the tenant context
 * (the row-level policy checks tenant_id = current_tenant()), so this service chooses the id first.
 * It is the only production code that sets a tenant context not derived from a login or a job.
 */
@Service
public class TenantBootstrapService {

    private final TenantRepository tenants;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public TenantBootstrapService(TenantRepository tenants, AppUserRepository users, PasswordEncoder passwordEncoder,
                                  AuditService audit, TransactionTemplate transaction) {
        this.tenants = tenants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.transaction = transaction;
    }

    public record NewTenant(UUID tenantId, LexvayUser admin) {
    }

    public NewTenant createTenant(String name, String adminEmail, String adminDisplayName, String adminPassword) {
        UUID tenantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        return TenantContext.runAs(tenantId, () -> transaction.execute(status -> {
            tenants.insert(tenantId, name, adminId);
            LexvayUser admin = insertUser(adminId, tenantId, adminEmail, adminDisplayName, Role.ADMIN, adminPassword, adminId);
            audit.recordAs(adminId, "TENANT_CREATED", "tenant", tenantId, Map.of("name", name));
            audit.recordAs(adminId, "USER_CREATED", "app_user", adminId, Map.of("email", admin.email(), "role", "ADMIN"));
            return new NewTenant(tenantId, admin);
        }));
    }

    /** Adds a user to an existing tenant on behalf of {@code actorId}. */
    public LexvayUser createUser(UUID tenantId, UUID actorId, String email, String displayName, Role role, String password) {
        UUID id = UUID.randomUUID();
        return TenantContext.runAs(tenantId, () -> transaction.execute(status -> {
            LexvayUser user = insertUser(id, tenantId, email, displayName, role, password, actorId);
            audit.recordAs(actorId, "USER_CREATED", "app_user", id, Map.of("email", user.email(), "role", role.name()));
            return user;
        }));
    }

    private LexvayUser insertUser(UUID id, UUID tenantId, String email, String displayName, Role role, String password,
                                  UUID createdBy) {
        String hash = password == null ? null : passwordEncoder.encode(password);
        users.insert(id, tenantId, email, displayName, role, hash, createdBy);
        return new LexvayUser(id, tenantId, email.toLowerCase(), displayName, role, hash, 0);
    }
}
