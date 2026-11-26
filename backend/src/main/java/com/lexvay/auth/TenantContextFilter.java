package com.lexvay.auth;

import com.lexvay.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Puts the authenticated user's tenant into {@link TenantContext} for the rest of the request. */
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.get();
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            TenantContext.runAs(user.get().tenantId(), () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new FilterException(e);
                }
            });
        } catch (FilterException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        }
    }

    private static final class FilterException extends RuntimeException {
        FilterException(Exception cause) {
            super(cause);
        }
    }
}
