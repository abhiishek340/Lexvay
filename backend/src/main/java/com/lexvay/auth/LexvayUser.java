package com.lexvay.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal: a tenant user with exactly one role. {@code securityStamp} is the value the user's
 * stamp had when the session was issued; {@link UserStampCheck} refuses the session once it differs (D-374).
 */
public record LexvayUser(UUID id, UUID tenantId, String email, String displayName, Role role, String passwordHash,
                         int securityStamp) implements UserDetails {

    public LexvayUser withPasswordHash(String hash) {
        return new LexvayUser(id, tenantId, email, displayName, role, hash, securityStamp);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
