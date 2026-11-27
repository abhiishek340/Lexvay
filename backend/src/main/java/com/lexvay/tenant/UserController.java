package com.lexvay.tenant;

import com.lexvay.auth.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the interface resolve user ids (audit actors, reviewers) to names. Any authenticated role may read it. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    public record UserSummary(UUID id, String displayName, Role role, String email) {
    }

    /** The caller's tenant only: row-level security on app_user scopes the query, not this code. */
    @GetMapping
    List<UserSummary> list() {
        return users.findAll().stream()
                .map(u -> new UserSummary(u.id(), u.displayName(), u.role(), u.email()))
                .toList();
    }
}
