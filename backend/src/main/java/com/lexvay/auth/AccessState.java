package com.lexvay.auth;

/** What a user may do right now, as the database says; compared with what the session was issued with (D-374). */
public record AccessState(boolean active, Role role, int securityStamp) {

    /** A user the database no longer shows: no session of it is current. */
    public static final AccessState GONE = new AccessState(false, null, -1);

    boolean matches(LexvayUser user) {
        return active && role == user.role() && securityStamp == user.securityStamp();
    }
}
