package org.openfilz.dms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.config.ApiVersion;
import org.openfilz.dms.dto.response.UploadResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@RequiredArgsConstructor
public abstract class TestContainersBaseConfig {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-bookworm");

    protected final WebTestClient webTestClient;
    protected final ObjectMapper objectMapper;


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    protected WebTestClient.ResponseSpec getUploadDocumentExchange(MultipartBodyBuilder builder, String accessToken) {
        return uploadDocument(getUploadDucumentHeader(builder).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    protected WebTestClient.ResponseSpec getUploadDocumentExchange(MultipartBodyBuilder builder) {
        return uploadDocument(getUploadDucumentHeader(builder));
    }

    protected UploadResponse uploadDocument(MultipartBodyBuilder builder) {
        return getUploadDocumentResponseBody(getUploadDucumentHeader(builder));
    }

    private UploadResponse getUploadDocumentResponseBody(WebTestClient.RequestHeadersSpec<?> uploadDucumentHeader) {
        return uploadDocument(uploadDucumentHeader)
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();
    }

    private WebTestClient.ResponseSpec uploadDocument(WebTestClient.RequestHeadersSpec<?> uploadDucumentHeader) {
        return uploadDucumentHeader
                .exchange();
    }

    private WebTestClient.RequestHeadersSpec<?> getUploadDucumentHeader(MultipartBodyBuilder builder) {
        return webTestClient.post().uri(uri -> uri.path(ApiVersion.API_PREFIX + "/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()));
    }

}
