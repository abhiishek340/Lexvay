package com.lexvay.tenant;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.CurrentUser;
import com.lexvay.auth.Role;
import com.lexvay.auth.UserStampCheck;
import com.lexvay.common.ConflictException;
import com.lexvay.common.NotFoundException;
import com.lexvay.common.TenantContext;
import com.lexvay.tenant.AppUserRepository.Account;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a tenant administrator does to the tenant's users (F5a): add a user, hand out a setup code, change a role,
 * withdraw or restore access. Users are never deleted (product rule 9). Every change is audited in its own
 * transaction, and every change to role or access moves the user's security stamp, which ends their open sessions
 * (D-374).
 */
@Service
public class UserAdminService {

    /** What an administrator gets back once: the setup code is not kept anywhere and cannot be shown again. */
    public record UserSetup(UserView user, String setupCode, OffsetDateTime expiresAt) {
    }

    private final AppUserRepository users;
    private final SetupTokenRepository tokens;
    private final AuditService audit;
    private final UserStampCheck stamps;

    public UserAdminService(AppUserRepository users, SetupTokenRepository tokens, AuditService audit, UserStampCheck stamps) {
        this.users = users;
        this.tokens = tokens;
        this.audit = audit;
        this.stamps = stamps;
    }

    @Transactional
    public UserSetup create(String email, String displayName, Role role) {
        UUID id = UUID.randomUUID();
        if (!users.insertWithoutPassword(id, TenantContext.require(), email.strip(), displayName.strip(), role,
                CurrentUser.idOrNull())) {
            throw new ConflictException("An account with this email exists");
        }
        Account account = account(id);
        audit.record("USER_CREATED", "app_user", id, Map.of("email", account.email(), "role", role.name()));
        return withNewCode(account);
    }

    /** A new code for a user who has none yet or lost the password; the earlier open codes stop working. */
    @Transactional
    public UserSetup issueSetupCode(UUID id) {
        Account account = account(id);
        tokens.closeOpenCodes(id);
        UserSetup setup = withNewCode(account);
        audit.record("USER_SETUP_CODE_ISSUED", "app_user", id,
                Map.of("email", account.email(), "expiresAt", setup.expiresAt().toString()));
        return setup;
    }

    @Transactional
    public Account changeRole(UUID id, Role role) {
        requireNotSelf(id, "You cannot change your own role");
        Locked locked = lockTarget(id);
        Account target = locked.account();
        if (target.role() == role) {
            return target;
        }
        if (locked.lastAdministrator() && role != Role.ADMIN) {
            throw lastAdministrator();
        }
        users.updateRole(id, role);
        audit.record("USER_ROLE_CHANGED", "app_user", id,
                Map.of("email", target.email(), "from", target.role().name(), "to", role.name()));
        stamps.evictAfterCommit(id);
        return account(id);
    }

    @Transactional
    public Account setActive(UUID id, boolean active) {
        requireNotSelf(id, "You cannot deactivate your own account");
        Locked locked = lockTarget(id);
        Account target = locked.account();
        if (target.active() == active) {
            return target;
        }
        if (locked.lastAdministrator() && !active) {
            throw lastAdministrator();
        }
        users.updateActive(id, active);
        audit.record(active ? "USER_REACTIVATED" : "USER_DEACTIVATED", "app_user", id, Map.of("email", target.email()));
        stamps.evictAfterCommit(id);
        return account(id);
    }

    /** The user a change applies to, and whether that user is the only active administrator of the tenant. */
    private record Locked(Account account, boolean lastAdministrator) {
    }

    /**
     * Locks the tenant's active administrators, then the target. The lock is what makes the last-administrator rule
     * hold when two administrators change each other at the same moment: the second waits, then sees the first one's
     * result (D-376). Locks are always taken in this order.
     */
    private Locked lockTarget(UUID id) {
        List<UUID> administrators = users.lockActiveAdministrators();
        Account target = users.lockAccount(id).orElseThrow(() -> new NotFoundException("User not found"));
        return new Locked(target, administrators.size() == 1 && administrators.contains(id));
    }

    private static ConflictException lastAdministrator() {
        return new ConflictException("At least one active administrator is required");
    }

    private static void requireNotSelf(UUID id, String message) {
        if (id.equals(CurrentUser.require().id())) {
            throw new ConflictException(message);
        }
    }

    private Account account(UUID id) {
        return users.findAccount(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserSetup withNewCode(Account account) {
        String code = SetupCodes.generate();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(SetupCodes.VALIDITY);
        tokens.insert(UUID.randomUUID(), TenantContext.require(), account.id(), SetupCodes.hash(code), expiresAt,
                CurrentUser.idOrNull());
        return new UserSetup(UserView.forAdministrator(account), code, expiresAt);
    }
}
