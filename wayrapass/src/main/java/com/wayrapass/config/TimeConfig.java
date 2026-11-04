package com.wayrapass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class TimeConfig {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
