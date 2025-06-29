package org.openfilz.dms.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfilz.dms.dto.*;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DocumentControllerAdditionalTests {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    @Mock
    private Authentication authentication;


    @Test
    void deleteDocumentMetadata_Success() {
        UUID documentId = UUID.randomUUID();
        DeleteMetadataRequest request = new DeleteMetadataRequest(List.of("key1"));

        Mockito.when(documentService.deleteDocumentMetadata(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(documentController.deleteDocumentMetadata(documentId, request, authentication))
                .expectNextMatches(response -> response.getStatusCode().equals(HttpStatus.NO_CONTENT))
                .verifyComplete();
    }

}