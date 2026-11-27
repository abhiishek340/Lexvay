package com.lexvay.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the response security headers before anything else runs (D-093). This is a servlet filter ahead of
 * the Spring Security chain rather than the Spring Security headers DSL because the request firewall rejects
 * some URLs before the chain starts, and those 400 responses must carry the headers too.
 */
public final class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'";
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";
    static final String HSTS = "max-age=31536000 ; includeSubDomains";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", HSTS);
        }
        chain.doFilter(request, response);
    }
}
