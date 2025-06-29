package org.openfilz.dms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfilz.dms.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerErrorHandlingTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DocumentController documentController;

    @Mock
    private Authentication authentication;

    @Test
    void downloadDocument_NotFound_ShouldReturnNotFound() {
        UUID documentId = UUID.randomUUID();

        when(documentService.findDocumentById(documentId)).thenReturn(Mono.empty());

        StepVerifier.create(documentController.downloadDocument(documentId, authentication))
                .expectNextMatches(response -> response.getStatusCode().equals(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateDocumentMetadata_InvalidRequest_ShouldReturnBadRequest() {
        UUID documentId = UUID.randomUUID();

        when(documentService.updateDocumentMetadata(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalArgumentException("Invalid metadata update request")));

        StepVerifier.create(documentController.updateDocumentMetadata(documentId, null, authentication))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().equals("Invalid metadata update request"))
                .verify();
    }
}