// src/test/java/com/example/dms/service/DocumentServiceImplTest.java
package org.openfilz.dms.service;

import org.openfilz.dms.dto.CreateFolderRequest;
import org.openfilz.dms.dto.DeleteRequest;
import org.openfilz.dms.dto.FolderResponse;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.exception.DuplicateNameException;
import org.openfilz.dms.repository.DocumentRepository;
import org.openfilz.dms.service.impl.DocumentServiceImpl;
import org.openfilz.dms.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Mock
    private JsonUtils jsonUtils;

    private Authentication mockAuthentication;

    @BeforeEach
    void setUp() {
        // documentService = new DocumentServiceImpl(documentRepository, storageService, objectMapper, auditService);
        // Mockito handles injection with @InjectMocks

        // Setup mock authentication
        mockAuthentication = new TestingAuthenticationToken("testuser", null, "ROLE_USER");
        // If using JWT:
        // Jwt mockJwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "testuser").build();
        // mockAuthentication = new JwtAuthenticationToken(mockJwt);




    }

    @Test
    void createFolder_success() {

        UUID parentId = UUID.randomUUID();
        CreateFolderRequest request = new CreateFolderRequest("New Folder", parentId);
        Document savedFolder = Document.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .parentId(request.parentId())
                .type(DocumentType.FOLDER)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy("testuser")
                .updatedBy("testuser")
                .metadata(Json.of("{}"))
                .build();

        when(documentRepository.existsByNameAndParentId(request.name(), request.parentId())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(savedFolder));
        when(auditService.logAction(anyString(), anyString(), anyString(), any(UUID.class), (Record) any())).thenReturn(Mono.empty());
        // when(storageService.createDirectory(anyString())).thenReturn(Mono.empty()); // If physical dir creation is involved

        Mono<FolderResponse> result = documentService.createFolder(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.name().equals(request.name()) &&
                        response.parentId().equals(request.parentId()))
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq("CREATE_FOLDER"), eq("FOLDER"),
                eq(UUID.fromString(savedFolder.getId().toString())), eq(request));
    }

    @Test
    void createFolder_atRoot_success() {
        CreateFolderRequest request = new CreateFolderRequest("Root Folder", null);
        Document savedFolder = Document.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .parentId(null)
                .type(DocumentType.FOLDER)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy("testuser")
                .updatedBy("testuser")
                .metadata(jsonUtils.emptyJson())
                .build();

        when(documentRepository.existsByNameAndParentIdIsNull(request.name())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(savedFolder));
        when(auditService.logAction(anyString(), anyString(), anyString(), any(UUID.class), (Record) any())).thenReturn(Mono.empty());

        Mono<FolderResponse> result = documentService.createFolder(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.name().equals(request.name()) &&
                        response.parentId() == null)
                .verifyComplete();

        verify(auditService).logAction(eq("testuser"), eq("CREATE_FOLDER"), eq("FOLDER"),
                eq(UUID.fromString(savedFolder.getId().toString())), eq(request));
    }

    @Test
    void createFolder_duplicateName_throwsException() {
        UUID parentId = UUID.randomUUID();
        CreateFolderRequest request = new CreateFolderRequest("Existing Folder", parentId);

        when(documentRepository.existsByNameAndParentId(request.name(), request.parentId())).thenReturn(Mono.just(true));

        Mono<FolderResponse> result = documentService.createFolder(request, mockAuthentication);

        StepVerifier.create(result)
                .expectError(DuplicateNameException.class)
                .verify();

    }

    /*@Test
    void uploadDocument_success() {
        UUID parentId = UUID.randomUUID();
        String filename = "file.txt";
        Map<String, Object> metadata = Map.of("key", "value");
        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(new org.springframework.http.HttpHeaders());
        when(documentRepository.existsByIdAndType(parentId, DocumentType.FOLDER)).thenReturn(Mono.just(true));
        when(documentRepository.existsByNameAndParentId(filename, parentId)).thenReturn(Mono.just(false));
        when(jsonUtils.toJson(metadata)).thenReturn(Json.of("{}"));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(Document.builder().id(UUID.randomUUID()).name(filename).type(DocumentType.FILE).build()));
        when(auditService.logAction(anyString(), anyString(), anyString(), any(UUID.class), any(Map.class))).thenReturn(Mono.empty());
        when(auditService.logAction(anyString(), anyString(), anyString(), any(UUID.class), any(Record.class))).thenReturn(Mono.empty());
        // Simuler DataBufferUtils.join
        org.springframework.core.io.buffer.DataBuffer dataBuffer = mock(org.springframework.core.io.buffer.DataBuffer.class);
        when(dataBuffer.readableByteCount()).thenReturn(123);
        when(filePart.content()).thenReturn(reactor.core.publisher.Flux.just(dataBuffer));
        // Simuler storageService
        StorageService storageService = mock(StorageService.class);
        when(storageService.saveFile(filePart)).thenReturn(Mono.just("storage/path"));
        // Injection manuelle du storageService
        documentService = new DocumentServiceImpl(documentRepository, storageService, new ObjectMapper(), auditService, jsonUtils);

        Mono<org.openfilz.dms.dto.UploadResponse> result = documentService.uploadDocument(filePart, parentId, metadata, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(resp -> resp.name().equals(filename))
                .verifyComplete();
    }*/

    /*
    @Test
    void uploadDocument_folderNotFound_shouldError() {
        UUID parentId = UUID.randomUUID();
        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn("file.txt");
        when(documentRepository.existsByIdAndType(parentId, DocumentType.FOLDER)).thenReturn(Mono.just(false));
        documentService = new DocumentServiceImpl(documentRepository, mock(StorageService.class), new ObjectMapper(), auditService, jsonUtils);

        Mono<org.openfilz.dms.dto.UploadResponse> result = documentService.uploadDocument(filePart, parentId, Map.of(), mockAuthentication);

        StepVerifier.create(result)
                .expectError(org.openfilz.dms.exception.DocumentNotFoundException.class)
                .verify();
    }
    */

    /*
    @Test
    void downloadDocument_success() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder().id(docId).type(DocumentType.FILE).storagePath("path").build();
        when(documentRepository.findById(docId)).thenReturn(Mono.just(doc));
        StorageService storageService = mock(StorageService.class);
        Resource resource = mock(Resource.class);
        when(storageService.loadFile("path")).thenReturn(Mono.just(resource));
        when(auditService.logAction(anyString(), anyString(), anyString(), any(UUID.class))).thenReturn(Mono.empty());
        documentService = new DocumentServiceImpl(documentRepository, storageService, new ObjectMapper(), auditService, jsonUtils);

        Mono<Resource> result = documentService.downloadDocument(docId, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(resource)
                .verifyComplete();
    }*/


    /*
    @Test
    void deleteFiles_notAFile_shouldError() {
        UUID fileId = UUID.randomUUID();
        DeleteRequest request = new DeleteRequest(java.util.List.of(fileId));
        Document folder = Document.builder().id(fileId).type(DocumentType.FOLDER).build();
        when(documentRepository.findById(fileId)).thenReturn(Mono.just(folder));
        documentService = new DocumentServiceImpl(documentRepository, mock(StorageService.class), new ObjectMapper(), auditService, jsonUtils);

        Mono<Void> result = documentService.deleteFiles(request, mockAuthentication);

        StepVerifier.create(result)
                .expectError(org.openfilz.dms.exception.OperationForbiddenException.class)
                .verify();
    }*/

    // ... More tests for other service methods (upload, download, delete, rename, etc.)
    // Test edge cases, error conditions, interactions with StorageService.
}