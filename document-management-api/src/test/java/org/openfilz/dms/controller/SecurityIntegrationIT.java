package org.openfilz.dms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openfilz.dms.config.ApiVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = ALL)
public class SecurityIntegrationIT extends TestContainersBaseConfig {

    @Container
    static final KeycloakContainer keycloak = new KeycloakContainer()
            .withRealmImportFile("keycloak/realm-export.json");

    private static String noaccessAccessToken;
    private static String auditAccessToken;
    private static String readerAccessToken;
    private static String contributorAccessToken;
    private static String adminAccessToken;

    public SecurityIntegrationIT(WebTestClient webTestClient, ObjectMapper objectMapper) {
        super(webTestClient, objectMapper);
    }

    @DynamicPropertySource
    static void registerResourceServerIssuerProperty(DynamicPropertyRegistry registry) {

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak.getAuthServerUrl() + "/realms/your-realm");
        registry.add("spring.security.no-auth", () -> false);
    }

    @BeforeAll
    static void startContainersAndGetTokens() {
        noaccessAccessToken = getAccessToken("test-user");
        auditAccessToken = getAccessToken("audit-user");
        readerAccessToken = getAccessToken("reader-user");
        contributorAccessToken = getAccessToken("contributor-user");
        adminAccessToken = getAccessToken("admin-user");
    }

    private static String getAccessToken(String username) {
        return WebClient.builder()
                .baseUrl(keycloak.getAuthServerUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .build()
                .post()
                .uri("/realms/your-realm/protocol/openid-connect/token")
                .body(
                            BodyInserters.fromFormData("grant_type", "password")
                                .with("client_id", "test-client")
                                .with("client_secret", "test-client-secret")
                                .with("username", username)
                                .with("password", "password")
                )
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        return new ObjectMapper().readTree(response).get("access_token").asText();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .block();
    }

    @Test
    void testAllEndpointsWithNoAccessRole() {

        webTestClient.get().uri(ApiVersion.API_PREFIX + "/folders/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + noaccessAccessToken)
                .exchange()
                .expectStatus().isForbidden();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        getUploadDocumentExchange(builder,  noaccessAccessToken).expectStatus().isForbidden();
        getUploadDocumentExchange(builder,  contributorAccessToken).expectStatus().isCreated();
        getUploadDocumentExchange(builder,  readerAccessToken).expectStatus().isForbidden();
        getUploadDocumentExchange(builder,  auditAccessToken).expectStatus().isForbidden();
        getUploadDocumentExchange(builder,  adminAccessToken).expectStatus().isCreated();
        getUploadDocumentExchange(builder).expectStatus().isUnauthorized();

        webTestClient.post().uri(ApiVersion.API_PREFIX + "/audit/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + noaccessAccessToken)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testAllEndpointsWithAdminRole() {
        webTestClient.get().uri(ApiVersion.API_PREFIX + "/audit/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk();

        webTestClient.post().uri(ApiVersion.API_PREFIX + "/audit/search")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testAllEndpointsWithNoRole() {
        webTestClient.get().uri(ApiVersion.API_PREFIX + "/audit/search")
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.post().uri(ApiVersion.API_PREFIX + "/audit/search")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
