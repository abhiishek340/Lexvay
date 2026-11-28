package com.lexvay.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** The wall clock in UTC; code that stamps a time takes this instead of calling {@code now()}, so a test can fix it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
