package com.wayrapass.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.simulator", name = "enabled", havingValue = "true")
public class SimulatorConfig {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService simulationExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "wayrapass-simulator-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newScheduledThreadPool(2, threads);
    }
}
