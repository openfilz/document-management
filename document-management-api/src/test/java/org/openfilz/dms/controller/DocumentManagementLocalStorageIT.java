package org.openfilz.dms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openfilz.dms.dto.*;
import org.openfilz.dms.enums.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class DocumentManagementLocalStorageIT {

    @Autowired
    protected WebTestClient webTestClient;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.5-bookworm");

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Test
    void whenUploadDocument_thenCreated() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("schema.sql");
    }

    @Test
    void whenUploadMultipleDocuments_thenCreated() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("file", new ClassPathResource("test.txt"));

        Map<String, Object> metadata1 = Map.of("helmVersion", "1.0");
        MultipleUploadFileParameter param1 = new MultipleUploadFileParameter("schema.sql", new MultipleUploadFileParameterAttributes(null, metadata1));
        Map<String, Object> metadata2 = Map.of("owner", "OpenFilz");
        MultipleUploadFileParameter param2 = new MultipleUploadFileParameter("test.txt", new MultipleUploadFileParameterAttributes(null, metadata2));

        List<UploadResponse> uploadResponse = webTestClient.post().uri(uri -> {
                    try {
                        return uri.path("/api/v1/documents/uploadMultiple")
                                        .queryParam("allowDuplicateFileNames", true)
                                        .queryParam("parametersByFilename[]", "{parametersByFilename}", "{parametersByFilename}")
                                        .build(objectMapper.writeValueAsString(param1),
                                                objectMapper.writeValueAsString(param2));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UploadResponse>>() {})
                .returnResult().getResponseBody();
        Assertions.assertNotNull(uploadResponse);
        UploadResponse uploadResponse1 = uploadResponse.get(0);
        Assertions.assertEquals(param1.filename(), uploadResponse1.name());
        UploadResponse uploadResponse2 = uploadResponse.get(1);
        Assertions.assertEquals(param2.filename(), uploadResponse2.name());

        checkFileInfo(uploadResponse1, param1, metadata1);
        checkFileInfo(uploadResponse2, param2, metadata2);

    }

    private void checkFileInfo(UploadResponse uploadResponse, MultipleUploadFileParameter param, Map<String, Object> metadata) {
        DocumentInfo info2 = webTestClient.get().uri(uri ->
                        uri.path("/api/v1/documents/{id}/info")
                                .queryParam("withMetadata", true)
                                .build(uploadResponse.id()))
                .exchange()
                .expectBody(DocumentInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(info2);
        Assertions.assertEquals(param.filename(), info2.name());
        Assertions.assertEquals(metadata, info2.metadata());
    }

    @Test
    void whenUploadTwiceSameDocument_thenConflict() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

    }

    @Test
    void whenSearchMetadata_thenOK() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", "MY_APP_1"));

        UploadResponse uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);

        Map<String, Object> metadata = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/{id}/search/metadata")
                        .build(uploadResponse.id()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(metadata);
        Assertions.assertEquals("OpenFilz", metadata.get("owner"));
        Assertions.assertEquals("MY_APP_1", metadata.get("appId"));

        metadata = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/{id}/search/metadata")
                        .build(uploadResponse.id()))
                .body(BodyInserters.fromValue(new SearchMetadataRequest(List.of("owner"))))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(metadata);
        Assertions.assertTrue(metadata.containsKey("owner"));
        Assertions.assertEquals("OpenFilz", metadata.get("owner"));
        Assertions.assertFalse(metadata.containsKey("appId"));
    }

    @Test
    void whenSearchIdsByMetadata_thenOK() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        UUID uuid = UUID.randomUUID();
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", uuid.toString()));

        UploadResponse uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);

        SearchByMetadataRequest searchByMetadataRequest = new SearchByMetadataRequest(null, null, null, null, Map.of("appId", uuid.toString()));

        List<UUID> uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(1, uuids.size());
        Assertions.assertEquals(uploadResponse.id(), uuids.getFirst());

        searchByMetadataRequest = new SearchByMetadataRequest(null, DocumentType.FILE, null, null, Map.of("appId", uuid.toString()));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(1, uuids.size());
        Assertions.assertEquals(uploadResponse.id(), uuids.getFirst());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FILE, null, null, Map.of("appId", uuid.toString()));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(1, uuids.size());
        Assertions.assertEquals(uploadResponse.id(), uuids.getFirst());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FILE, null, true, Map.of("appId", uuid.toString()));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(1, uuids.size());
        Assertions.assertEquals(uploadResponse.id(), uuids.getFirst());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FOLDER, null, true, Map.of("appId", uuid.toString()));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(0, uuids.size());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FOLDER, UUID.randomUUID(), true, Map.of("appId", uuid.toString()));

        webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().is4xxClientError();

        searchByMetadataRequest = new SearchByMetadataRequest(null, null, null, null, null);

        webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().is4xxClientError();

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FILE, null, true, null);

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertFalse(uuids.isEmpty());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", DocumentType.FILE, null, null, null);

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertFalse(uuids.isEmpty());

        searchByMetadataRequest = new SearchByMetadataRequest("schema.sql", null, null, null, null);

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertFalse(uuids.isEmpty());


        //test if 2 files are retrieved
        builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "Joe", "appId", uuid.toString()));

        UploadResponse uploadResponse2 = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse2);

        searchByMetadataRequest = new SearchByMetadataRequest(null, null, null, null, Map.of("appId", uuid.toString()));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(2, uuids.size());
        Assertions.assertTrue(uuids.contains(uploadResponse.id()));
        Assertions.assertTrue(uuids.contains(uploadResponse2.id()));

        searchByMetadataRequest = new SearchByMetadataRequest(null, null, null, null, Map.of("appId", uuid.toString(), "owner", "Joe"));

        uuids = webTestClient.post().uri("/api/v1/documents/search/ids-by-metadata")
                .body(BodyInserters.fromValue(searchByMetadataRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UUID>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uuids);
        Assertions.assertEquals(1, uuids.size());
        Assertions.assertTrue(uuids.contains(uploadResponse2.id()));
    }

    @Test
    void whenSearchMetadata_thenNotFound() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", "MY_APP_1"));

        UploadResponse uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);

        Map<String, Object> metadata = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/{id}/search/metadata")
                        .build(uploadResponse.id()))
                .body(BodyInserters.fromValue(new SearchMetadataRequest(List.of("owner", "appId"))))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(metadata);
        Assertions.assertTrue(metadata.containsKey("owner"));
        Assertions.assertEquals("OpenFilz", metadata.get("owner"));
        Assertions.assertTrue(metadata.containsKey("appId"));
        Assertions.assertEquals("MY_APP_1", metadata.get("appId"));

        metadata = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/{id}/search/metadata")
                        .build(uploadResponse.id()))
                .body(BodyInserters.fromValue(new SearchMetadataRequest(List.of("owner1", "appId"))))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .returnResult().getResponseBody();

        Assertions.assertNotNull(metadata);
        Assertions.assertFalse(metadata.containsKey("owner1"));
        Assertions.assertFalse(metadata.containsKey("owner"));
        Assertions.assertTrue(metadata.containsKey("appId"));
        Assertions.assertEquals("MY_APP_1", metadata.get("appId"));

        webTestClient.post().uri(uri -> uri.path("/api/v1/documents/{id}/search/metadata")
                        .build(UUID.randomUUID().toString()))
                .exchange()
                .expectStatus().isNotFound();


    }

    @Test
    void whenReplaceContent_thenOK() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse originalUploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(originalUploadResponse);
        UUID id = originalUploadResponse.id();
        Long originalSize = originalUploadResponse.size();
        Assertions.assertTrue(originalSize != null   && originalSize > 0);
        builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("test.txt"));
        webTestClient.put().uri(uri -> uri.path("/api/v1/documents/{id}/replace-content")
                        .build(id.toString()))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("schema.sql")
                .jsonPath("$.type").isEqualTo(DocumentType.FILE)
                .jsonPath("$.id").isEqualTo(id.toString());

        DocumentInfo info = webTestClient.get().uri(uri ->
                        uri.path("/api/v1/documents/{id}/info")
                                .queryParam("withMetadata", true)
                                .build(id.toString()))
                .exchange()
                .expectBody(DocumentInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(info);
        Assertions.assertTrue(info.size() != null && info.size() > 0 && !info.size().equals(originalSize));

    }

    @Test
    void whenReplaceMetadata_thenOK() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", "MY_APP_1"));

        UploadResponse originalUploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(originalUploadResponse);
        UUID id = originalUploadResponse.id();
        Long originalSize = originalUploadResponse.size();
        Assertions.assertTrue(originalSize != null   && originalSize > 0);
        Map<String, Object> newMetadata = Map.of("owner", "Google", "clientId", "Joe");
        webTestClient.put().uri(uri -> uri.path("/api/v1/documents/{id}/replace-metadata")
                        .build(id.toString()))
                .body(BodyInserters.fromValue(newMetadata))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("schema.sql")
                .jsonPath("$.type").isEqualTo(DocumentType.FILE)
                .jsonPath("$.id").isEqualTo(id.toString());

        DocumentInfo info = webTestClient.get().uri(uri ->
                        uri.path("/api/v1/documents/{id}/info")
                                .queryParam("withMetadata", true)
                                .build(id.toString()))
                .exchange()
                .expectBody(DocumentInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(info);
        Assertions.assertTrue(info.metadata() != null && info.metadata().equals(newMetadata));

    }

    @Test
    void whenDownloadDocument_thenOk() throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        webTestClient.get().uri("/api/v1/documents/{id}/download", response.id())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/x-sql")
                .expectBody(String.class).isEqualTo(new String(new ClassPathResource("schema.sql").getInputStream().readAllBytes()));
    }

    @Test
    void whenDownloadDocument_thenNotFound() {
        webTestClient.get().uri("/api/v1/documents/{id}/download", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void whenDownloadDocumentMultiple_thenOk() throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        ClassPathResource file1 = new ClassPathResource("schema.sql");
        builder.part("file", file1);
        ClassPathResource file2 = new ClassPathResource("test.txt");
        builder.part("file", file2);

        List<UploadResponse> uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/uploadMultiple")
                .queryParam("allowDuplicateFileNames", true)
                .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UploadResponse>>() {})
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);
        UploadResponse uploadResponse1 = uploadResponse.get(0);
        UploadResponse uploadResponse2 = uploadResponse.get(1);

        Resource resource = webTestClient.post().uri("/api/v1/documents/download-multiple")
                .body(BodyInserters.fromValue(List.of(uploadResponse1.id(), uploadResponse2.id())))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectBody(Resource.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(resource);
        Assertions.assertEquals("documents.zip", resource.getFilename());

        Path targetFolder = Files.createTempDirectory(UUID.randomUUID().toString());
        unzip(resource, targetFolder);
        int n = 0;
        int k = 0;

        Path temp = Files.createTempDirectory(UUID.randomUUID().toString());
        Path tmpFile1 = temp.resolve(file1.getFilename());
        try (InputStream is = file1.getInputStream()) {
            Files.copy(is, tmpFile1);
        }
        Path tmpFile2 = temp.resolve(file2.getFilename());
        try (InputStream is = file2.getInputStream()) {
            Files.copy(is, tmpFile2);
        }



        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetFolder)) {
            for (Path file : stream) {
                if(file.getFileName().toString().equals(file1.getFilename())) {
                    Assertions.assertEquals(-1L, Files.mismatch(file, tmpFile1));
                    n++;
                } else if(file.getFileName().toString().equals(file2.getFilename())) {
                    Assertions.assertEquals(-1L, Files.mismatch(file, tmpFile2));
                    n++;
                } else {
                    k++;
                }
            }
        }
        Assertions.assertEquals(2, n);
        Assertions.assertEquals(0, k);

    }

    public static void unzip(final Resource zipFile, final Path targetFolder) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Channels.newInputStream(Channels.newChannel(zipFile.getInputStream())))) {
            for (ZipEntry entry = zipInputStream.getNextEntry(); entry != null; entry = zipInputStream.getNextEntry()) {
                Path toPath = targetFolder.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectory(toPath);
                } else try (FileChannel fileChannel = FileChannel.open(toPath, WRITE, CREATE/*, DELETE_ON_CLOSE*/)) {
                    fileChannel.transferFrom(Channels.newChannel(zipInputStream), 0, Long.MAX_VALUE);
                }
            }
        }
        log.debug("File {} unzipped in {}", zipFile.getFilename(), targetFolder);
    }


    @Test
    void whenDeleteDocument_thenNoContent() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        DeleteRequest deleteRequest = new DeleteRequest(Collections.singletonList(response.id()));

        webTestClient.method(HttpMethod.DELETE).uri("/api/v1/files")
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/api/v1/documents/{id}/info", response.id())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void whenDeleteMetadata_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", "MY_APP_1"));

        UploadResponse uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);

        DeleteMetadataRequest deleteRequest = new DeleteMetadataRequest(Collections.singletonList("owner"));

        webTestClient.method(HttpMethod.DELETE).uri(uri -> uri.path("/api/v1/documents/{id}/metadata").build(uploadResponse.id()))
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().isNoContent();

        DocumentInfo info = webTestClient.get().uri(uri ->
                        uri.path("/api/v1/documents/{id}/info")
                                .queryParam("withMetadata", true)
                                .build(uploadResponse.id()))
                .exchange()
                .expectBody(DocumentInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(info);
        Assertions.assertEquals("schema.sql", info.name());
        Assertions.assertFalse(info.metadata().containsKey("owner"));
        Assertions.assertTrue(info.metadata().containsKey("appId"));
    }

    @Test
    void whenUpdateMetadata_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("metadata", Map.of("owner", "OpenFilz", "appId", "MY_APP_1"));

        UploadResponse uploadResponse = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(uploadResponse);

        UpdateMetadataRequest updateMetadataRequest = new UpdateMetadataRequest(Map.of("owner", "Joe", "appId",  "MY_APP_2"));

        webTestClient.method(HttpMethod.PATCH).uri(uri -> uri.path("/api/v1/documents/{id}/metadata").build(uploadResponse.id()))
                .body(BodyInserters.fromValue(updateMetadataRequest))
                .exchange()
                .expectStatus().isOk();

        DocumentInfo info = webTestClient.get().uri(uri ->
                        uri.path("/api/v1/documents/{id}/info")
                                .queryParam("withMetadata", true)
                                .build(uploadResponse.id()))
                .exchange()
                .expectBody(DocumentInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(info);
        Assertions.assertEquals("schema.sql", info.name());
        Assertions.assertEquals("Joe", info.metadata().get("owner"));
        Assertions.assertEquals("MY_APP_2", info.metadata().get("appId"));
    }

    @Test
    void whenUpdateOrDeleteMetadataWithNoKeys_thenError() {


        UpdateMetadataRequest updateMetadataRequest = new UpdateMetadataRequest(Map.of());

        webTestClient.method(HttpMethod.PATCH).uri(uri -> uri.path("/api/v1/documents/{id}/metadata").build(UUID.randomUUID().toString()))
                .body(BodyInserters.fromValue(updateMetadataRequest))
                .exchange()
                .expectStatus().is4xxClientError();

        DeleteMetadataRequest deleteRequest = new DeleteMetadataRequest(Collections.emptyList());

        webTestClient.method(HttpMethod.DELETE).uri(uri -> uri.path("/api/v1/documents/{id}/metadata").build(UUID.randomUUID().toString()))
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().is4xxClientError();


    }

    //FileController Tests

    @Test
    void whenMoveFile_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createFolderRequest = new CreateFolderRequest("test-folder-a", null);

        UploadResponse folderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        MoveRequest moveRequest = new MoveRequest(Collections.singletonList(response.id()), folderResponse.id(), false);

        webTestClient.post().uri("/api/v1/files/move")
                .body(BodyInserters.fromValue(moveRequest))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri(uri -> uri.path("/api/v1/folders/list")
                        .queryParam("folderId", folderResponse.id())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(response.id().toString());
    }

    @Test
    void whenCopyFile_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri("/api/v1/documents/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createFolderRequest = new CreateFolderRequest("test-folder-b", null);

        UploadResponse folderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        CopyRequest copyRequest = new CopyRequest(Collections.singletonList(response.id()), folderResponse.id(), false);

        webTestClient.post().uri("/api/v1/files/copy")
                .body(BodyInserters.fromValue(copyRequest))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri(uri -> uri.path("/api/v1/folders/list")
                        .queryParam("folderId", folderResponse.id())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo(response.name());

        webTestClient.get().uri("/api/v1/documents/{id}/info", response.id())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void whenRenameFile_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        RenameRequest renameRequest = new RenameRequest("new-name.sql");

        webTestClient.put().uri("/api/v1/files/{fileId}/rename", response.id())
                .body(BodyInserters.fromValue(renameRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("new-name.sql");
    }

    @Test
    void whenDeleteFile_thenOk() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));

        UploadResponse response = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .queryParam("allowDuplicateFileNames", true)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        DeleteRequest deleteRequest = new DeleteRequest(Collections.singletonList(response.id()));

        webTestClient.method(org.springframework.http.HttpMethod.DELETE).uri("/api/v1/files")
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri("/api/v1/documents/{id}/info", response.id())
                .exchange()
                .expectStatus().isNotFound();
    }

    //FolderController tests
    @Test
    void whenCreateFolder_thenOk() {
        CreateFolderRequest createFolderRequest = new CreateFolderRequest("test-folder", null);

        webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("test-folder");
    }

    @Test
    void whenMoveFolder_thenOk() {
        CreateFolderRequest createFolderRequest1 = new CreateFolderRequest("test-folder-1", null);

        FolderResponse folderResponse1 = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest1))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createFolderRequest2 = new CreateFolderRequest("test-folder-2", null);

        FolderResponse folderResponse2 = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest2))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        MoveRequest moveRequest = new MoveRequest(Collections.singletonList(folderResponse1.id()), folderResponse2.id(), false);

        webTestClient.post().uri("/api/v1/folders/move")
                .body(BodyInserters.fromValue(moveRequest))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/folders/list?folderId={id}", folderResponse2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(folderResponse1.id().toString());
    }

    @Test
    void whenCopyFolder_thenOk() {
        CreateFolderRequest createFolderRequest1 = new CreateFolderRequest("test-folder-to-copy", null);

        FolderResponse folderResponse1 = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest1))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createFolderRequest2 = new CreateFolderRequest("target-folder", null);

        FolderResponse folderResponse2 = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest2))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CopyRequest copyRequest = new CopyRequest(Collections.singletonList(folderResponse1.id()), folderResponse2.id(), false);

        webTestClient.post().uri("/api/v1/folders/copy")
                .body(BodyInserters.fromValue(copyRequest))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/folders/list?folderId={id}", folderResponse2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo(folderResponse1.name());

        webTestClient.get().uri("/api/v1/folders/list?folderId={id}", folderResponse1.id())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void whenCopyFolderRecursive_thenOk() {
        CreateFolderRequest createSourceFolderRequest = new CreateFolderRequest("test-folder-source", null);

        FolderResponse sourceFolderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createSourceFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createSourceSubFolderRequest = new CreateFolderRequest("test-subfolder-source", sourceFolderResponse.id());

        FolderResponse sourceSubFolderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createSourceSubFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("parentFolderId", sourceFolderResponse.id().toString());

        UploadResponse sourceRootFile = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("test.txt"));
        builder.part("parentFolderId", sourceSubFolderResponse.id().toString());

        UploadResponse sourceSubFolderFile = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createFolderRequest2 = new CreateFolderRequest("test-folder-target", null);

        FolderResponse folderResponse2 = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest2))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CopyRequest copyRequest = new CopyRequest(Collections.singletonList(sourceFolderResponse.id()), folderResponse2.id(), false);

        webTestClient.post().uri("/api/v1/folders/copy")
                .body(BodyInserters.fromValue(copyRequest))
                .exchange()
                .expectStatus().isOk();

        List<FolderElementInfo> targetFolderInfoList = webTestClient.get().uri("/api/v1/folders/list?folderId={id}", folderResponse2.id())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FolderElementInfo.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(targetFolderInfoList);
        Assertions.assertEquals(1, targetFolderInfoList.size());
        Assertions.assertTrue(targetFolderInfoList.stream().anyMatch(resp->resp.type().equals(DocumentType.FOLDER) && resp.name().equals("test-folder-source")));

        FolderElementInfo targetFolderRoot = targetFolderInfoList.stream().filter(resp -> resp.type().equals(DocumentType.FOLDER) && resp.name().equals("test-folder-source")).findAny().get();

        List<FolderElementInfo> targetFolderRootInfoList = webTestClient.get().uri("/api/v1/folders/list?folderId={id}", targetFolderRoot.id())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FolderElementInfo.class)
                .returnResult().getResponseBody();

        Assertions.assertNotNull(targetFolderRootInfoList);
        Assertions.assertEquals(2, targetFolderRootInfoList.size());
        Assertions.assertTrue(targetFolderRootInfoList.stream().anyMatch(resp->resp.type().equals(DocumentType.FILE) && resp.name().equals("schema.sql")));
        Assertions.assertTrue(targetFolderRootInfoList.stream().anyMatch(resp->resp.type().equals(DocumentType.FOLDER) && resp.name().equals("test-subfolder-source")));

        FolderElementInfo subFolderInfo = targetFolderRootInfoList.stream().filter(resp -> resp.type().equals(DocumentType.FOLDER) && resp.name().equals("test-subfolder-source")).findAny().get();

        List<FolderElementInfo> targetSubFolderInfoList = webTestClient.get().uri("/api/v1/folders/list?folderId={id}", subFolderInfo.id())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FolderElementInfo.class)
                .returnResult().getResponseBody();
        Assertions.assertNotNull(targetSubFolderInfoList);
        Assertions.assertEquals(1, targetSubFolderInfoList.size());
        Assertions.assertTrue(targetSubFolderInfoList.stream().anyMatch(resp->resp.type().equals(DocumentType.FILE) && resp.name().equals("test.txt")));

    }

    @Test
    void whenDeleteFolderRecursive_thenOk() {
        CreateFolderRequest createSourceFolderRequest = new CreateFolderRequest("test-delete-folder-source", null);

        FolderResponse sourceFolderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createSourceFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        CreateFolderRequest createSourceSubFolderRequest = new CreateFolderRequest("test-delete-subfolder-source", sourceFolderResponse.id());

        FolderResponse sourceSubFolderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createSourceSubFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("schema.sql"));
        builder.part("parentFolderId", sourceFolderResponse.id().toString());

        UploadResponse sourceRootFile = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        builder = new MultipartBodyBuilder();
        builder.part("file", new ClassPathResource("test.txt"));
        builder.part("parentFolderId", sourceSubFolderResponse.id().toString());

        UploadResponse sourceSubFolderFile = webTestClient.post().uri(uri -> uri.path("/api/v1/documents/upload")
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UploadResponse.class)
                .returnResult().getResponseBody();

        DeleteRequest deleteRequest = new DeleteRequest(Collections.singletonList(sourceFolderResponse.id()));

        webTestClient.method(org.springframework.http.HttpMethod.DELETE).uri("/api/v1/folders")
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri(uri -> uri.path("/api/v1/documents/{id}/info")
                        .build(sourceFolderResponse.id()))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get().uri(uri -> uri.path("/api/v1/documents/{id}/info")
                        .build(sourceSubFolderResponse.id()))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get().uri(uri -> uri.path("/api/v1/documents/{id}/info")
                        .build(sourceRootFile.id()))
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get().uri(uri -> uri.path("/api/v1/documents/{id}/info")
                        .build(sourceSubFolderFile.id()))
                .exchange()
                .expectStatus().isNotFound();

    }

    @Test
    void whenRenameFolder_thenOk() {
        CreateFolderRequest createFolderRequest = new CreateFolderRequest("folder-to-rename", null);

        FolderResponse folderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        RenameRequest renameRequest = new RenameRequest("renamed-folder");

        webTestClient.put().uri("/api/v1/folders/{folderId}/rename", folderResponse.id())
                .body(BodyInserters.fromValue(renameRequest))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("renamed-folder");
    }

    @Test
    void whenDeleteFolder_thenOk() {
        CreateFolderRequest createFolderRequest = new CreateFolderRequest("folder-to-delete", null);

        FolderResponse folderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        DeleteRequest deleteRequest = new DeleteRequest(Collections.singletonList(folderResponse.id()));

        webTestClient.method(org.springframework.http.HttpMethod.DELETE).uri("/api/v1/folders")
                .body(BodyInserters.fromValue(deleteRequest))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get().uri(uri -> uri.path("/api/v1/folders/list")
                        .queryParam("folderId", folderResponse.id())
                        .build())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void whenListFolder_thenOk() {
        CreateFolderRequest createFolderRequest = new CreateFolderRequest("folder-to-list", null);

        FolderResponse folderResponse = webTestClient.post().uri("/api/v1/folders")
                .body(BodyInserters.fromValue(createFolderRequest))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(FolderResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertEquals("folder-to-list", folderResponse.name());

        List<FolderResponse> folders = webTestClient.get().uri("/api/v1/folders/list")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(FolderResponse.class)
                .returnResult().getResponseBody();

        Assertions.assertTrue(folders.stream().anyMatch(f -> f.name().equals("folder-to-list")));

    }

    @Test
    void whenListFolder_thenError() {
        webTestClient.get()
                .uri(uri -> uri.path("/api/v1/folders/list")
                    .queryParam("onlyFiles", true)
                    .queryParam("onlyFolders", true)
                    .build())
                .exchange()
                .expectStatus().is4xxClientError();

        webTestClient.get()
                .uri(uri -> uri.path("/api/v1/folders/list")
                        .queryParam("onlyFiles", true)
                        .build())
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri(uri -> uri.path("/api/v1/folders/list")
                        .queryParam("onlyFolders", true)
                        .build())
                .exchange()
                .expectStatus().isOk();

    }
}
