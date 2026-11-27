package com.lexvay.auth;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.web.SpaRoutes;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Form login against tenant users by default; single sign-on through OIDC when profile {@code oidc} is
 * active. Responses are JSON, not redirects, because the client is a single-page app. CSRF uses the SPA
 * cookie pattern that Angular's HttpClient understands (XSRF-TOKEN cookie, X-XSRF-TOKEN header). Response
 * headers and rate limits are described in D-092 and D-093.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/v1/auth/login";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";
    public static final String SESSION_URL = "/api/v1/auth/session";
    /** Where the OIDC provider sends the browser back to; counted against the login budget. */
    public static final String OIDC_CALLBACK_PATTERN = "/login/oauth2/code/*";

    private final ObjectMapper json;
    private final AuditService audit;
    private final ProblemWriter problems;
    private final RateLimitProperties rateLimits;

    public SecurityConfig(ObjectMapper json, AuditService audit, ProblemWriter problems, RateLimitProperties rateLimits) {
        this.json = json;
        this.audit = audit;
        this.problems = problems;
        this.rateLimits = rateLimits;
    }

    /** Outermost servlet filter, so even the firewall's own 400 responses carry the headers. */
    @Bean
    FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        var registration = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<OidcLogin> oidcLogin) throws Exception {
        // Cookie and header names are set explicitly to what Angular's HttpClient uses by default.
        // The handler reads the token on every request, so the cookie is always present for the SPA.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                // Frame options and cache control stay at their secure defaults; the headers of D-093 come from
                // SecurityHeadersFilter, which also covers requests the firewall rejects.
                .headers(headers -> headers
                        .contentTypeOptions(options -> options.disable())
                        .httpStrictTransportSecurity(hsts -> hsts.disable()))
                // A new session id at login defeats session fixation (D-094).
                .sessionManagement(sessions -> sessions.sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(LOGIN_URL, SESSION_URL).permitAll()
                        .requestMatchers(SpaRoutes.APP_REQUESTS).permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler((request, response, authentication) -> {
                            LexvayUser user = (LexvayUser) authentication.getPrincipal();
                            TenantContext.runAs(user.tenantId(), () ->
                                    audit.record("LOGIN_SUCCEEDED", "app_user", user.id(),
                                            Map.of("email", user.email(), "method", "password")));
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            json.writeValue(response.getOutputStream(), AuthController.toView(user));
                        })
                        .failureHandler((request, response, exception) ->
                                problems.write(response, HttpStatus.UNAUTHORIZED, "Email or password is incorrect")))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof LexvayUser user) {
                                TenantContext.runAs(user.tenantId(), () ->
                                        audit.record("LOGOUT", "app_user", user.id(), Map.of()));
                            }
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        }))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(response, HttpStatus.FORBIDDEN, "This action is not allowed for your role")))
                // Before CSRF so that every login attempt counts, with or without a valid token.
                .addFilterBefore(new RateLimitFilter(rateLimits, problems, System::nanoTime), CsrfFilter.class)
                .addFilterBefore(new TenantContextFilter(), AuthorizationFilter.class);
        OidcLogin oidc = oidcLogin.getIfAvailable();
        if (oidc != null) {
            oidc.applyTo(http);
        }
        return http.build();
    }
}
