package com.lexvay.auth;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import com.lexvay.observability.ProblemDetailRequestId;
import com.lexvay.web.SpaRoutes;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Local profile: form login against seeded tenant users (spec section 4). Responses are JSON, not
 * redirects, because the client is a single-page app. CSRF uses the SPA cookie pattern that Angular's
 * HttpClient understands (XSRF-TOKEN cookie, X-XSRF-TOKEN header).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String LOGIN_URL = "/api/v1/auth/login";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";
    public static final String SESSION_URL = "/api/v1/auth/session";

    private final ObjectMapper json;
    private final AuditService audit;

    public SecurityConfig(ObjectMapper json, AuditService audit) {
        this.json = json;
        this.audit = audit;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Cookie and header names are set explicitly to what Angular's HttpClient uses by default.
        // The handler reads the token on every request, so the cookie is always present for the SPA.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName("XSRF-TOKEN");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
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
                                    audit.record("LOGIN_SUCCEEDED", "app_user", user.id(), Map.of("email", user.email())));
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            json.writeValue(response.getOutputStream(), AuthController.toView(user));
                        })
                        .failureHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Email or password is incorrect")))
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
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "This action is not allowed for your role")))
                .addFilterBefore(new TenantContextFilter(), AuthorizationFilter.class);
        return http.build();
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        ProblemDetailRequestId.stamp(problem);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
