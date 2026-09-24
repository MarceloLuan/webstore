package com.webstore.backend.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ReservaConfig {
    @Bean public Clock estoqueClock() { return Clock.systemUTC(); }
}
