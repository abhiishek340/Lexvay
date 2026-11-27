package com.lexvay.tenant;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.UserStampCheck;
import com.lexvay.common.TenantContext;
import com.lexvay.tenant.SetupTokenRepository.OpenCode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lets a person who holds a setup code choose a password (D-373). Nobody is signed in yet, so the code is looked up
 * before the tenant is known; everything that changes data then runs under the code's tenant, in one transaction.
 */
@Service
public class PasswordSetupService {

    static final int MIN_CHARACTERS = 12;
    /** bcrypt, the application's encoder, refuses anything longer; shorter text is not silently cut instead. */
    static final int MAX_BYTES = 72;

    private final SetupTokenRepository tokens;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final UserStampCheck stamps;
    private final TransactionTemplate transaction;

    public PasswordSetupService(SetupTokenRepository tokens, AppUserRepository users, PasswordEncoder passwordEncoder,
                                AuditService audit, UserStampCheck stamps, TransactionTemplate transaction) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.stamps = stamps;
        this.transaction = transaction;
    }

    /**
     * @throws IllegalArgumentException (a 400) when the code is not valid, with the same message whether it is
     *         unknown, used or expired, or when the password breaks a rule
     */
    public void setPassword(String code, String password) {
        OpenCode open = tokens.findOpen(SetupCodes.hash(code))
                .filter(found -> SetupCodes.matches(code, found.tokenHash()))
                .orElseThrow(PasswordSetupService::invalidCode);
        requireAcceptable(password, open.email());
        String hash = passwordEncoder.encode(password);
        UUID userId = open.userId();
        TenantContext.runAs(open.tenantId(), () -> transaction.executeWithoutResult(status -> {
            if (!tokens.markUsed(open.id())) {
                throw invalidCode(); // used or expired between the lookup and now
            }
            users.setPassword(userId, hash);
            audit.recordAs(userId, "PASSWORD_SET", "app_user", userId, Map.of());
            stamps.evictAfterCommit(userId);
        }));
    }

    private static void requireAcceptable(String password, String email) {
        if (password.codePointCount(0, password.length()) < MIN_CHARACTERS) {
            throw new IllegalArgumentException("Password must have at least " + MIN_CHARACTERS + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Password must be at most " + MAX_BYTES + " bytes long in UTF-8");
        }
        if (password.equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Password must not be the same as the email");
        }
    }

    private static IllegalArgumentException invalidCode() {
        return new IllegalArgumentException("This setup code is not valid");
    }
}
