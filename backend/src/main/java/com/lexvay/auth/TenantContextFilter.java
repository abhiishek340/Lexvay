package com.lexvay.auth;

import com.lexvay.common.LogContext;
import com.lexvay.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the authenticated user's tenant into {@link TenantContext} and the user id into the log context for the rest
 * of the request. A session whose user's access changed since it was issued ends here with a 401 (D-374).
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final UserStampCheck stamps;
    private final ProblemWriter problems;

    public TenantContextFilter(UserStampCheck stamps, ProblemWriter problems) {
        this.stamps = stamps;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var user = CurrentUser.get();
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (!TenantContext.runAs(user.get().tenantId(), () -> stamps.isCurrent(user.get()))) {
            endSession(request, response);
            return;
        }
        try (LogContext.Scope ignored = LogContext.with(LogContext.USER_ID, user.get().id())) {
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

    private void endSession(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        problems.write(response, HttpStatus.UNAUTHORIZED, "Your access changed. Sign in again.");
    }

    private static final class FilterException extends RuntimeException {
        FilterException(Exception cause) {
            super(cause);
        }
    }
}
