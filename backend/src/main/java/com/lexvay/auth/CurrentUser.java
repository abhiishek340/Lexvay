package com.lexvay.auth;

import java.util.Optional;
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

    public static LexvayUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
