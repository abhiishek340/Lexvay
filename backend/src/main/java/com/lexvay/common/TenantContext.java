package com.lexvay.common;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant the current thread works for. Set from the authenticated user (web requests), from the
 * claimed job (worker) or by {@code TenantBootstrapService} when a tenant is created. Every database
 * connection handed out while a tenant is set carries it as the session setting {@code lexvay.tenant_id}
 * that the row-level security policies read.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        return current().orElseThrow(() -> new IllegalStateException("No tenant context is set"));
    }

    public static <T> T runAs(UUID tenantId, Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        runAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    /** Runs with no tenant at all: the database then returns and accepts no tenant rows. */
    public static <T> T runWithoutTenant(Supplier<T> action) {
        UUID previous = CURRENT.get();
        CURRENT.remove();
        try {
            return action.get();
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            }
        }
    }
}
