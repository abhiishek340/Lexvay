package com.lexvay.auth;

import com.lexvay.tenant.AppUserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Maps a provider identity to a Lexvay account (D-091). The provider has already authenticated the user and
 * verified the ID token; this service decides who the user is in Lexvay. Tenant and role come only from the
 * {@code app_user} row found by the verified email claim, never from token claims.
 */
@Profile("oidc")
@Component
class LexvayOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    static final String NO_ACCOUNT = "no_lexvay_account";

    /** The standard OIDC user, carrying the Lexvay account the identity resolved to. */
    static final class LexvayOidcUser extends DefaultOidcUser {

        private final LexvayUser account;

        LexvayOidcUser(OidcUser oidc, LexvayUser account) {
            super(account.getAuthorities(), oidc.getIdToken(), oidc.getUserInfo());
            this.account = account;
        }

        LexvayUser account() {
            return account;
        }
    }

    private final OidcUserService delegate = new OidcUserService();
    private final AppUserRepository users;

    LexvayOidcUserService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidc = delegate.loadUser(request);
        String email = oidc.getEmail();
        if (email == null || email.isBlank() || emailIsUnverified(oidc.getClaims().get("email_verified"))) {
            throw noAccount();
        }
        LexvayUser account = users.findForLogin(email).orElseThrow(LexvayOidcUserService::noAccount);
        return new LexvayOidcUser(oidc, account);
    }

    /** Providers send a boolean or the string "true"/"false"; anything that is not "true" is not verified. */
    private static boolean emailIsUnverified(Object claim) {
        return claim != null && !Boolean.parseBoolean(String.valueOf(claim));
    }

    private static OAuth2AuthenticationException noAccount() {
        return new OAuth2AuthenticationException(new OAuth2Error(NO_ACCOUNT));
    }
}
