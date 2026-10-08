package com.HyperLocalSystem.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostGIS container per test JVM. Spring caches the context (and with it this container)
 * across test classes that share the same configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final DockerImageName POSTGIS = DockerImageName.parse("postgis/postgis:18-3.6")
            .asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGIS);
    }

    @Bean
    CapturingOtpSender capturingOtpSender() {
        return new CapturingOtpSender();
    }
}
