package com.cloud.metadata;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base test configuration for integration tests using Testcontainers.
 * Provides a shared PostgreSQL container for all tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * PostgreSQL container shared across all tests.
     * 
     * @ServiceConnection automatically configures Spring Boot DataSource
     *                    properties.
     */
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")
                .withReuse(true); // Reuse container across test classes for speed
    }
}
