package com.lexvay.observability;

import com.lexvay.common.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates one request across the log lines, the response header and the problem details it produces.
 * A client may supply {@code X-Request-Id} when it is 8 to 64 letters, digits or hyphens; anything else is
 * ignored and a UUID is generated. Runs before Spring Security so rejected requests carry an id too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = accepted(request.getHeader(HEADER));
        response.setHeader(HEADER, requestId);
        try (LogContext.Scope ignored = LogContext.with(LogContext.REQUEST_ID, requestId)) {
            chain.doFilter(request, response);
        }
    }

    static String accepted(String supplied) {
        return supplied != null && ACCEPTED.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
    }
}
