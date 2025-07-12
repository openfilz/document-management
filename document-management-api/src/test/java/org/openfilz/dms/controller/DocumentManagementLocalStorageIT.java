package org.openfilz.dms.controller;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openfilz.dms.dto.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.context.TestConstructor.AutowireMode.ALL;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestConstructor(autowireMode = ALL)
class DocumentManagementLocalStorageIT extends ContainersConfiguration {


    public DocumentManagementLocalStorageIT(WebTestClient webTestClient) {
        super(webTestClient);
    }

    @Test
    void whenUploadDocument_thenOk() {
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
    void whenDeleteDocument_thenOk() {
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
}
