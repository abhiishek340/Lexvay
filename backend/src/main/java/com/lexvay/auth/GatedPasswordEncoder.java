package com.lexvay.auth;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Every {@code encode} and {@code matches}, whatever the algorithm, goes through the {@link PasswordHashGate} (D-407). */
final class GatedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final PasswordHashGate gate;

    GatedPasswordEncoder(PasswordEncoder delegate, PasswordHashGate gate) {
        this.delegate = delegate;
        this.gate = gate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return gate.run(() -> delegate.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return gate.run(() -> delegate.matches(rawPassword, encodedPassword));
    }

    /** Reads the parameters inside the hash and hashes nothing, so it needs no permit. */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
