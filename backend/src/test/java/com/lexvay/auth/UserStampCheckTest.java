package com.lexvay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The per-instance cache of D-374 with a fake clock: one lookup per 15 seconds, and a change is seen after at most that. */
class UserStampCheckTest {

    private final UUID id = UUID.randomUUID();
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger lookups = new AtomicInteger();
    private volatile AccessState database = new AccessState(true, Role.REVIEWER, 4);
    private final UserStampCheck check = new UserStampCheck(user -> {
        lookups.incrementAndGet();
        return database;
    }, nanos::get);

    private LexvayUser session(Role role, int stamp) {
        return new LexvayUser(id, UUID.randomUUID(), "u@x.test", "U", role, "hash", stamp);
    }

    private void pass(Duration time) {
        nanos.addAndGet(time.toNanos());
    }

    @Test
    void aSessionIssuedWithTheCurrentValuesIsCurrent() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
    }

    @Test
    void aDifferentStampARoleChangeOrAnInactiveUserEndsTheSession() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 3))).as("older stamp").isFalse();
        assertThat(check.isCurrent(session(Role.REVIEWER, 5))).as("newer stamp").isFalse();
        assertThat(check.isCurrent(session(Role.SIGNER, 4))).as("other role").isFalse();
        database = new AccessState(false, Role.REVIEWER, 4);
        pass(Duration.ofSeconds(16));
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).as("inactive").isFalse();
    }

    @Test
    void aUserTheDatabaseNoLongerShowsHasNoCurrentSession() {
        database = AccessState.GONE;
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isFalse();
    }

    @Test
    void theDatabaseIsAskedOncePerUserAndWindow() {
        for (int i = 0; i < 50; i++) {
            check.isCurrent(session(Role.REVIEWER, 4));
        }
        assertThat(lookups).hasValue(1);
        pass(Duration.ofSeconds(14));
        check.isCurrent(session(Role.REVIEWER, 4));
        assertThat(lookups).as("still inside 15 seconds").hasValue(1);
        pass(Duration.ofSeconds(2));
        check.isCurrent(session(Role.REVIEWER, 4));
        assertThat(lookups).as("after 15 seconds").hasValue(2);
    }

    @Test
    void aChangeMadeElsewhereIsSeenWithinFifteenSeconds() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
        database = new AccessState(true, Role.REVIEWER, 5);
        pass(Duration.ofSeconds(14));
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).as("cached, so not yet").isTrue();
        pass(Duration.ofSeconds(2));
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).as("expired, so read again").isFalse();
    }

    @Test
    void aChangeMadeHereIsSeenAtOnce() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
        database = new AccessState(false, Role.REVIEWER, 5);
        check.evictAfterCommit(id);
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isFalse();
    }

    @Test
    void evictionWaitsForTheCommit() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
        database = new AccessState(false, Role.REVIEWER, 5);
        TransactionSynchronizationManager.initSynchronization();
        try {
            check.evictAfterCommit(id);
            assertThat(check.isCurrent(session(Role.REVIEWER, 4))).as("not committed yet").isTrue();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(check.isCurrent(session(Role.REVIEWER, 4))).as("committed").isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aRolledBackChangeDoesNotEvict() {
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
        database = new AccessState(false, Role.REVIEWER, 5);
        TransactionSynchronizationManager.initSynchronization();
        try {
            check.evictAfterCommit(id);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(1));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        assertThat(check.isCurrent(session(Role.REVIEWER, 4))).isTrue();
    }

    @Test
    void theCacheIsBoundedAt10000Users() {
        for (int i = 0; i < 12_000; i++) {
            check.isCurrent(new LexvayUser(UUID.randomUUID(), UUID.randomUUID(), "u@x.test", "U", Role.REVIEWER, "hash", 4));
        }
        assertThat(check.size()).isLessThanOrEqualTo(UserStampCheck.MAX_USERS);
    }
}
