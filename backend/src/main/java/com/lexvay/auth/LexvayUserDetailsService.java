package com.lexvay.auth;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.tenant.AppUserRepository;
import java.util.Map;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finds the account for a form login, and replaces its hash when the sign-in proved the password and the hash is
 * not what the encoder would write now (D-401). Spring calls {@link #updatePassword} only after a successful match.
 */
@Service
public class LexvayUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final AppUserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transaction;

    public LexvayUserDetailsService(AppUserRepository users, AuditService audit, TransactionTemplate transaction) {
        this.users = users;
        this.audit = audit;
        this.transaction = transaction;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findForLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    /** One UPDATE and one audit entry (the user is the actor), in one transaction under the user's tenant. */
    @Override
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        LexvayUser account = (LexvayUser) user;
        boolean replaced = TenantContext.runAs(account.tenantId(), () -> transaction.execute(status -> {
            if (!users.rehashPassword(account.id(), account.passwordHash(), newPassword)) {
                return false;
            }
            audit.recordAs(account.id(), "PASSWORD_REHASHED", "app_user", account.id(),
                    Map.of("from", PasswordEncoders.algorithmOf(account.passwordHash()),
                            "to", PasswordEncoders.algorithmOf(newPassword)));
            return true;
        }));
        return replaced ? account.withPasswordHash(newPassword) : account;
    }
}
