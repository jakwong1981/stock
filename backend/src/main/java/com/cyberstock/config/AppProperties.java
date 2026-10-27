package com.cyberstock.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly-typed application settings bound from the {@code app.*} namespace. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Market market) {

    public record Jwt(String secret, long ttlMinutes) {}

    public record Cors(String allowedOrigins) {
        public List<String> originList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    public record Market(String provider, long tickIntervalMs, String finnhubApiKey, long finnhubPollSeconds,
                         long yahooPollSeconds) {}
}
