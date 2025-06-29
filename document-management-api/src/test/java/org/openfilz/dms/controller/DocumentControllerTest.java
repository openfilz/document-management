package org.openfilz.dms.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfilz.dms.dto.*;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    @Mock
    private FilePart filePart;

    @Mock
    private Resource resource;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authentication = new TestingAuthenticationToken("testuser", "password");
    }

    @Test
    void uploadDocument_Success() {
        UUID documentId = UUID.randomUUID();
        String filename = "test.txt";
        Map<String, Object> metadata = new HashMap<>();
        UUID parentId = UUID.randomUUID();

        when(documentService.uploadDocument(any(), any(), any(), any(), false, any()))
                .thenReturn(Mono.just(new UploadResponse(documentId, filename, null, null)));

        StepVerifier.create(documentController.uploadDocument(filePart, parentId.toString(), null, 100L, false, authentication))
                .expectNextMatches(response -> 
                    response.getStatusCode().is2xxSuccessful() &&
                    response.getBody().id().equals(documentId) &&
                    response.getBody().name().equals(filename)
                )
                .verifyComplete();
    }

    @Test
    void downloadDocument_Success() {
        UUID documentId = UUID.randomUUID();
        Document doc = Document.builder()
                .id(documentId)
                .name("test.txt")
                .contentType("text/plain")
                .type(DocumentType.FILE)
                .build();

        when(documentService.findDocumentById(documentId)).thenReturn(Mono.just(doc));
        when(documentService.downloadDocument(documentId, authentication)).thenReturn(Mono.just(resource));

        StepVerifier.create(documentController.downloadDocument(documentId, authentication))
                .expectNextMatches(response -> 
                    response.getStatusCode().is2xxSuccessful() &&
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains(doc.getName()) &&
                    response.getHeaders().getContentType().equals(MediaType.parseMediaType(doc.getContentType()))
                )
                .verifyComplete();
    }

    @Test
    void getDocumentMetadata_Success() {
        UUID documentId = UUID.randomUUID();
        Map<String, Object> metadata = Map.of("key1", "value1", "key2", "value2");
        SearchMetadataRequest request = new SearchMetadataRequest(null);

        when(documentService.getDocumentMetadata(documentId, request, authentication))
                .thenReturn(Mono.just(metadata));

        StepVerifier.create(documentController.getDocumentMetadata(documentId, request, authentication))
                .expectNextMatches(response -> 
                    response.getStatusCode().is2xxSuccessful() &&
                            Objects.equals(response.getBody(), metadata)
                )
                .verifyComplete();
    }

    @Test
    void getDocumentInfo_Success() {
        UUID documentId = UUID.randomUUID();
        DocumentInfo info = new DocumentInfo(DocumentType.FILE, "test.txt", null, null, null);

        when(documentService.getDocumentInfo(documentId, false, authentication))
                .thenReturn(Mono.just(info));

        StepVerifier.create(documentController.getDocumentInfo(documentId, false, authentication))
                .expectNextMatches(response -> 
                    response.getStatusCode().is2xxSuccessful() &&
                    Objects.requireNonNull(response.getBody()).type().equals(DocumentType.FILE) &&
                    response.getBody().name().equals("test.txt")
                )
                .verifyComplete();
    }
}