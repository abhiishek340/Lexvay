package com.lexvay.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<LexvayUser> get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LexvayUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The signed-in user's id, or null for work done by the system (a job, the scheduler). */
    public static UUID idOrNull() {
        return get().map(LexvayUser::id).orElse(null);
    }

    public static LexvayUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
