package com.lexvay.auth;

import org.springframework.security.authentication.AuthenticationServiceException;

/**
 * Every password hash is in use and none became free within the wait (D-407). It is an authentication exception so that
 * a sign-in carries it to the failure handler; the setup endpoint answers it through the API's exception handler. Both
 * answer 503 with {@code Retry-After}.
 */
public class PasswordHashBusyException extends AuthenticationServiceException {

    public static final String MESSAGE = "Sign-in is busy. Try again in a moment.";
    public static final int RETRY_AFTER_SECONDS = 2;

    public PasswordHashBusyException() {
        super(MESSAGE);
    }
}
