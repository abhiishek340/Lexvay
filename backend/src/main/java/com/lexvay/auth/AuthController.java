package com.lexvay.auth;

import com.lexvay.common.TenantContext;
import com.lexvay.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TenantRepository tenants;
    private final List<String> demoTenantNames;

    public AuthController(TenantRepository tenants, @Value("${lexvay.demo-tenant-names:Northfield University}") List<String> demoTenantNames) {
        this.tenants = tenants;
        this.demoTenantNames = demoTenantNames;
    }

    public record UserView(UUID id, UUID tenantId, String email, String displayName, Role role) {
    }

    static UserView toView(LexvayUser user) {
        return new UserView(user.id(), user.tenantId(), user.email(), user.displayName(), user.role());
    }

    /** {@code demo} drives the permanent fictional-data banner (D-064). */
    public record SessionView(boolean authenticated, UserView user, String csrfHeaderName, String tenantName, boolean demo) {
    }

    /**
     * Public. The single-page app calls this first to learn whether a session exists and which header
     * carries the CSRF token; the response also sets the XSRF-TOKEN cookie needed for the login request.
     */
    @GetMapping("/session")
    SessionView session(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String headerName = token == null ? null : token.getHeaderName();
        String tenantName = CurrentUser.get().isPresent()
                ? TenantContext.runAs(CurrentUser.require().tenantId(), () -> tenants.findById(CurrentUser.require().tenantId()).map(t -> t.name()).orElse(null))
                : null;
        boolean demo = tenantName != null && demoTenantNames.contains(tenantName);
        return new SessionView(CurrentUser.get().isPresent(), CurrentUser.get().map(AuthController::toView).orElse(null), headerName, tenantName, demo);
    }

    @GetMapping("/me")
    UserView me() {
        return toView(CurrentUser.require());
    }
}
