package com.lexvay.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record UserView(UUID id, UUID tenantId, String email, String displayName, Role role) {
    }

    static UserView toView(LexvayUser user) {
        return new UserView(user.id(), user.tenantId(), user.email(), user.displayName(), user.role());
    }

    public record SessionView(boolean authenticated, UserView user, String csrfHeaderName) {
    }

    /**
     * Public. The single-page app calls this first to learn whether a session exists and which header
     * carries the CSRF token; the response also sets the XSRF-TOKEN cookie needed for the login request.
     */
    @GetMapping("/session")
    SessionView session(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String headerName = token == null ? null : token.getHeaderName();
        return new SessionView(CurrentUser.get().isPresent(), CurrentUser.get().map(AuthController::toView).orElse(null), headerName);
    }

    @GetMapping("/me")
    UserView me() {
        return toView(CurrentUser.require());
    }
}
