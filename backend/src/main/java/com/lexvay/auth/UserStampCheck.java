package com.lexvay.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.lexvay.tenant.AppUserRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ends sessions whose user's access changed (D-374). A session carries the stamp it was issued with; this check
 * compares it, and the user's role and activity, with the current values. The current values come from a per-instance
 * cache so a busy user costs one statement per {@link #TTL}, not one per request. The instance that makes a change
 * evicts the user at once; any other instance sees it after at most the TTL.
 */
@Component
public class UserStampCheck {

    /** The longest another instance can keep honouring a session whose access was withdrawn. */
    static final Duration TTL = Duration.ofSeconds(15);
    static final int MAX_USERS = 10_000;

    private final Cache<UUID, AccessState> current;
    private final Function<UUID, AccessState> load;

    @Autowired
    UserStampCheck(AppUserRepository users) {
        this(id -> users.findAccess(id).orElse(AccessState.GONE), Ticker.systemTicker());
    }

    /** @param load reads the user's current access in the caller's tenant; the ticker is a fake in tests */
    UserStampCheck(Function<UUID, AccessState> load, Ticker ticker) {
        this.load = load;
        this.current = Caffeine.newBuilder().maximumSize(MAX_USERS).expireAfterWrite(TTL).ticker(ticker).build();
    }

    /** Must run with the user's tenant bound, because the lookup is under row-level security. */
    public boolean isCurrent(LexvayUser user) {
        return current.get(user.id(), load).matches(user);
    }

    /** For tests: runs pending evictions and returns the number of users held. */
    long size() {
        current.cleanUp();
        return current.estimatedSize();
    }

    /**
     * Forgets the user once the surrounding transaction has committed. Evicting earlier would let a request that
     * arrives before the commit cache the old values again for a full TTL.
     */
    public void evictAfterCommit(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            current.invalidate(userId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                current.invalidate(userId);
            }
        });
    }
}
