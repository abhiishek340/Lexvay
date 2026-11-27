package com.lexvay.observability;

import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Lets a scraper read the actuator endpoints on the management port without a session. The matcher
 * {@link EndpointRequest#toAnyEndpoint()} only matches in the management server's own context, so the
 * main port keeps requiring sign-in for everything but the app, the probes and the login endpoints; the
 * configuration is not even created when the management port is the main port.
 */
@Configuration
@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
class ManagementSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain managementFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
