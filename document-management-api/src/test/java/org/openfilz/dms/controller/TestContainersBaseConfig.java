package org.openfilz.dms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.config.ApiVersion;
import org.openfilz.dms.dto.request.MultipleUploadFileParameter;
import org.openfilz.dms.dto.response.UploadResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Arrays;

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
        return uploadDocument(addAuthorization(getUploadDucumentHeader(builder), accessToken));
    }

    protected WebTestClient.RequestHeadersSpec<?> addAuthorization( WebTestClient.RequestHeadersSpec<?> header, String accessToken) {
        return header.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    protected WebTestClient.ResponseSpec getUploadDocumentExchange(MultipartBodyBuilder builder) {
        return uploadDocument(getUploadDucumentHeader(builder));
    }

    protected UploadResponse uploadDocument(MultipartBodyBuilder builder) {
        return getUploadDocumentResponseBody(getUploadDucumentHeader(builder));
    }

    protected UploadResponse getUploadDocumentResponseBody(WebTestClient.RequestHeadersSpec<?> uploadDucumentHeader) {
        return uploadDocument(uploadDucumentHeader)
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();
    }

    protected WebTestClient.ResponseSpec uploadDocument(WebTestClient.RequestHeadersSpec<?> uploadDucumentHeader) {
        return uploadDucumentHeader
                .exchange();
    }

    protected WebTestClient.RequestHeadersSpec<?> getUploadDucumentHeader(MultipartBodyBuilder builder) {
        return webTestClient.post().uri(uri -> uri.path(ApiVersion.API_PREFIX + "/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()));
    }

    protected UploadResponse getUploadResponse(MultipartBodyBuilder builder) {
        return webTestClient.post().uri(ApiVersion.API_PREFIX + "/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();
    }

    protected MultipartBodyBuilder newFileBuilder() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        return builder;
    }

    protected MultipartBodyBuilder newFileBuilder(String... filenames) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        Arrays.stream(filenames).forEach(filename->builder.part("file", new ClassPathResource(filename)));
        return builder;
    }

    protected WebTestClient.ResponseSpec getUploadMultipleDocumentExchange(MultipleUploadFileParameter param1,
                                                                           MultipleUploadFileParameter param2,
                                                                           MultipartBodyBuilder builder,
                                                                           String accessToken) {
        return getUploadMultipleDocumentExchangeHeader(param1, param2, builder)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchange();
    }

    protected WebTestClient.ResponseSpec getUploadMultipleDocumentExchange(MultipleUploadFileParameter param1,
                                                                           MultipleUploadFileParameter param2,
                                                                           MultipartBodyBuilder builder) {
        return getUploadMultipleDocumentExchangeHeader(param1, param2, builder)
                .exchange();
    }

    private WebTestClient.RequestHeadersSpec<?> getUploadMultipleDocumentExchangeHeader(MultipleUploadFileParameter param1, MultipleUploadFileParameter param2, MultipartBodyBuilder builder) {
        return webTestClient.post().uri(uri -> {
                    try {
                        return uri.path(ApiVersion.API_PREFIX + "/documents/upload-multiple")
                                .queryParam("allowDuplicateFileNames", true)
                                .queryParam("parametersByFilename[]", "{parametersByFilename}", "{parametersByFilename}")
                                .build(objectMapper.writeValueAsString(param1),
                                        objectMapper.writeValueAsString(param2));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()));
    }

}
