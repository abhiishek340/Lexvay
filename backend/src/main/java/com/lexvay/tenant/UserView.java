package com.lexvay.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lexvay.auth.Role;
import com.lexvay.tenant.AppUserRepository.Account;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user in the API. Every role may read id, name, role and email (the interface resolves user ids to names);
 * {@code active}, {@code createdAt} and {@code passwordSet} are for administrators and are left out for everyone else.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserView(UUID id, String displayName, Role role, String email, Boolean active, OffsetDateTime createdAt,
                       Boolean passwordSet) {

    static UserView forMember(Account account) {
        return new UserView(account.id(), account.displayName(), account.role(), account.email(), null, null, null);
    }

    static UserView forAdministrator(Account account) {
        return new UserView(account.id(), account.displayName(), account.role(), account.email(), account.active(),
                account.createdAt(), account.passwordSet());
    }
}
