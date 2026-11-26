package com.lexvay.common;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sends a CSRF token the way the browser does: XSRF-TOKEN cookie plus X-XSRF-TOKEN header with the same
 * value. Spring Security's own {@code csrf()} post-processor is not used because it replaces the live
 * CsrfFilter's repository with an HttpSession one for the rest of the JVM (DECISIONS.md D-038), which
 * would make tests pass against plumbing that production does not have.
 */
public final class TestCsrf {

    private TestCsrf() {
    }

    public static RequestPostProcessor csrf() {
        String token = UUID.randomUUID().toString();
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }
}
