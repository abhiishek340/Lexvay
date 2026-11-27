package com.lexvay.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * One line per API request that reached a handler: method, path, status and duration. It runs inside the
 * security filter chain, so the line carries the request, tenant and user ids from the logging context. The
 * query string is never logged because it can hold search text.
 */
@Component
class ApiAccessLog implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger("lexvay.access");
    private static final String STARTED = ApiAccessLog.class.getName() + ".started";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(STARTED, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long millis = (System.nanoTime() - (long) request.getAttribute(STARTED)) / 1_000_000;
        log.info("{} {} {} {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
    }
}
