package com.lexvay.auth;

import com.lexvay.audit.AuditService;
import com.lexvay.auth.LexvayOidcUserService.LexvayOidcUser;
import com.lexvay.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Single sign-on through an OpenID Connect provider, active only in profile {@code oidc} (D-090). The browser
 * starts at {@code /oauth2/authorization/lexvay}. After the provider authenticates the user, the session
 * principal is replaced by the same {@link LexvayUser} that form login produces, so tenant binding, method
 * security and audit do not know which way the user signed in.
 */
@Profile("oidc")
@Component
public class OidcLogin {

    private static final Logger log = LoggerFactory.getLogger(OidcLogin.class);

    private final LexvayOidcUserService accounts;
    private final AuditService audit;
    private final ProblemWriter problems;
    private final String postLoginUrl;
    private final SecurityContextRepository contexts = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

    OidcLogin(LexvayOidcUserService accounts, AuditService audit, ProblemWriter problems,
              @Value("${lexvay.oidc.post-login-url:/}") String postLoginUrl) {
        this.accounts = accounts;
        this.audit = audit;
        this.problems = problems;
        this.postLoginUrl = postLoginUrl;
    }

    public void applyTo(HttpSecurity http) throws Exception {
        http.oauth2Login(login -> login
                .userInfoEndpoint(info -> info.oidcUserService(accounts))
                .successHandler(this::onSuccess)
                .failureHandler(this::onFailure));
    }

    private void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        LexvayUser user = ((LexvayOidcUser) authentication.getPrincipal()).account();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contexts.saveContext(context, request, response);
        TenantContext.runAs(user.tenantId(), () ->
                audit.record("LOGIN_SUCCEEDED", "app_user", user.id(), Map.of("email", user.email(), "method", "oidc")));
        response.sendRedirect(postLoginUrl);
    }

    private void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof OAuth2AuthenticationException oauth
                && LexvayOidcUserService.NO_ACCOUNT.equals(oauth.getError().getErrorCode())) {
            problems.write(response, HttpStatus.FORBIDDEN, "No Lexvay account for this identity");
            return;
        }
        log.warn("OIDC sign-in failed: {}", exception.getMessage());
        problems.write(response, HttpStatus.UNAUTHORIZED, "Sign-in with the identity provider failed");
    }
}
