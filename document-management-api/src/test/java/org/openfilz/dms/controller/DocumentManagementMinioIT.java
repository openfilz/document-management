package org.openfilz.dms.controller;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = ALL)
public class DocumentManagementMinioIT extends DocumentManagementLocalStorageIT {

    public DocumentManagementMinioIT(WebTestClient webTestClient) {
        super(webTestClient);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.type", () -> "minio");
    }
}
