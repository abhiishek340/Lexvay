package com.lexvay.web;

import java.io.IOException;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular bundle that the image copies to {@code classpath:/static/}. Files with a content hash
 * in their name are cached for a year; everything else, including the app shell, is revalidated on every
 * load so a new release is picked up at once. A GET that matches no file and names no extension returns the
 * shell so deep links work; {@code /api} and {@code /actuator} never do, they stay 404 problem details.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
class SpaResourceConfig implements WebMvcConfigurer {

    private static final String LOCATION = "classpath:/static/";
    private static final String SHELL = "static/index.html";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/*.js", "/*.css", "/media/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new ShellFallbackResolver());
    }

    /** Resolves files normally; for an unknown route path it resolves the app shell instead. */
    static final class ShellFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource file = super.getResource(resourcePath, location);
            if (file != null || SpaRoutes.isService("/" + resourcePath) || !SpaRoutes.isRoute(resourcePath)) {
                return file;
            }
            Resource shell = new ClassPathResource(SHELL);
            return shell.isReadable() ? shell : null;
        }
    }
}
