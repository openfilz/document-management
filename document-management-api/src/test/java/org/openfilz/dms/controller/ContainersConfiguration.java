package org.openfilz.dms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;


@RequiredArgsConstructor
public abstract class ContainersConfiguration {

    protected final WebTestClient webTestClient;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-bookworm");

    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-06-13T11-33-47Z-cpuv1");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        registry.add("storage.minio.endpoint", minio::getS3URL);
        registry.add("storage.minio.access-key", minio::getUserName);
        registry.add("storage.minio.secret-key", minio::getPassword);
    }

}
