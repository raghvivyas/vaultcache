package com.vaultcache.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers custom Micrometer metrics for rate limiter observability.
 * Exposed at /actuator/prometheus for Prometheus scraping.
 */
@Configuration
public class MicrometerConfig {

    @Bean
    public Counter allowedRequestsCounter(MeterRegistry registry) {
        return Counter.builder("vaultcache.requests.allowed")
                .description("Total requests allowed through the rate limiter")
                .register(registry);
    }

    @Bean
    public Counter throttledRequestsCounter(MeterRegistry registry) {
        return Counter.builder("vaultcache.requests.throttled")
                .description("Total requests throttled by the rate limiter")
                .register(registry);
    }

    @Bean
    public Timer redisLatencyTimer(MeterRegistry registry) {
        return Timer.builder("vaultcache.redis.latency")
                .description("Redis Lua script execution latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
