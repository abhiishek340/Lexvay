package com.lexvay.auth;

import com.lexvay.tenant.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LexvayUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public LexvayUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findForLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
