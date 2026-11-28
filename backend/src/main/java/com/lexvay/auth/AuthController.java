package com.lexvay.auth;

import com.lexvay.common.TenantContext;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.PasswordSetupService;
import com.lexvay.tenant.Tenant;
import com.lexvay.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** The sizes only bound what a request can carry; the password rules are checked, and named, by the service. */
    public record SetupRequest(@NotBlank @Size(max = 200) String code, @NotNull @Size(max = 1000) String password) {
    }

    private final TenantRepository tenants;
    private final PasswordSetupService passwordSetup;

    public AuthController(TenantRepository tenants, PasswordSetupService passwordSetup) {
        this.tenants = tenants;
        this.passwordSetup = passwordSetup;
    }

    public record UserView(UUID id, UUID tenantId, String email, String displayName, Role role) {
    }

    static UserView toView(LexvayUser user) {
        return new UserView(user.id(), user.tenantId(), user.email(), user.displayName(), user.role());
    }

    /** {@code demo} is true for a DEMO tenant and drives the permanent fictional-data banner (D-064, D-560). */
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
        Optional<Tenant> tenant = CurrentUser.get()
                .flatMap(user -> TenantContext.runAs(user.tenantId(), () -> tenants.findById(user.tenantId())));
        return new SessionView(CurrentUser.get().isPresent(), CurrentUser.get().map(AuthController::toView).orElse(null), headerName,
                tenant.map(Tenant::name).orElse(null), tenant.map(t -> t.dataMode() == DataMode.DEMO).orElse(false));
    }

    /**
     * Public, and counted against the login budget. A person with a setup code chooses a password; the code works once.
     * Does not sign anybody in: the client goes to the sign-in screen (D-373).
     */
    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setup(@Valid @RequestBody SetupRequest request) {
        passwordSetup.setPassword(request.code(), request.password());
    }

    @GetMapping("/me")
    UserView me() {
        return toView(CurrentUser.require());
    }
}
