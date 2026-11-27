package com.lexvay.tenant;

import com.lexvay.auth.CurrentUser;
import com.lexvay.auth.Role;
import com.lexvay.tenant.UserAdminService.UserSetup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The tenant's users. Any authenticated role may read the list (the interface resolves user ids to names);
 * administrators also see their state and are the only ones who change anything (F5a).
 */
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {

    public record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(max = 200) String displayName,
                                    @NotNull Role role) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    private final AppUserRepository users;
    private final UserAdminService admin;

    public UserController(AppUserRepository users, UserAdminService admin) {
        this.users = users;
        this.admin = admin;
    }

    /** The caller's tenant only: row-level security on app_user scopes the query, not this code. */
    @GetMapping
    List<UserView> list() {
        boolean administrator = CurrentUser.require().role() == Role.ADMIN;
        return users.listAccounts().stream()
                .map(a -> administrator ? UserView.forAdministrator(a) : UserView.forMember(a))
                .toList();
    }

    /** The response carries the only copy of the setup code; the administrator hands it to the new user. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    UserSetup create(@Valid @RequestBody CreateUserRequest request) {
        return admin.create(request.email(), request.displayName(), request.role());
    }

    @PostMapping("/{id}/setup-code")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    UserSetup issueSetupCode(@PathVariable UUID id) {
        return admin.issueSetupCode(id);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    UserView changeRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return UserView.forAdministrator(admin.changeRole(id, request.role()));
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    UserView setActive(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) {
        return UserView.forAdministrator(admin.setActive(id, request.active()));
    }
}
