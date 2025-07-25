package org.openfilz.dms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.r2dbc.postgresql.codec.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfilz.dms.dto.*;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.AuditAction;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.exception.DocumentNotFoundException;
import org.openfilz.dms.exception.DuplicateNameException;
import org.openfilz.dms.exception.OperationForbiddenException;
import org.openfilz.dms.repository.DocumentDAO;
import org.openfilz.dms.repository.DocumentRepository;
import org.openfilz.dms.service.impl.DocumentServiceImpl;
import org.openfilz.dms.utils.JsonUtils;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.openfilz.dms.enums.AuditAction.*;
import static org.openfilz.dms.enums.DocumentType.FILE;
import static org.openfilz.dms.enums.DocumentType.FOLDER;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentDAO documentDAO;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JsonUtils jsonUtils;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private Authentication mockAuthentication;

    @BeforeEach
    void setUp() {
        mockAuthentication = new TestingAuthenticationToken("testuser", null, "ROLE_USER");
    }

    @Test
    void createFolder_success() {
        UUID parentId = UUID.randomUUID();
        CreateFolderRequest request = new CreateFolderRequest("New Folder", parentId);
        Document savedFolder = Document.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .parentId(request.parentId())
                .type(FOLDER)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy("testuser")
                .updatedBy("testuser")
                .metadata(Json.of("{}"))
                .build();

        when(documentRepository.existsByIdAndType(parentId, FOLDER)).thenReturn(Mono.just(true));
        when(documentRepository.existsByNameAndParentId(request.name(), request.parentId())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(savedFolder));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), (Record) any())).thenReturn(Mono.empty());

        Mono<FolderResponse> result = documentService.createFolder(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.name().equals(request.name()) &&
                        response.parentId().equals(request.parentId()))
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(CREATE_FOLDER), eq(FOLDER),
                eq(savedFolder.getId()), eq(request));
    }

    @Test
    void createFolder_atRoot_success() {
        CreateFolderRequest request = new CreateFolderRequest("Root Folder", null);
        Document savedFolder = Document.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .parentId(null)
                .type(FOLDER)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy("testuser")
                .updatedBy("testuser")
                .metadata(jsonUtils.emptyJson())
                .build();

        when(documentRepository.existsByNameAndParentIdIsNull(request.name())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(savedFolder));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), (Record) any())).thenReturn(Mono.empty());

        Mono<FolderResponse> result = documentService.createFolder(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(response -> response.name().equals(request.name()) &&
                        response.parentId() == null)
                .verifyComplete();

        verify(auditService).logAction(eq("testuser"), eq(CREATE_FOLDER), eq(FOLDER),
                eq(savedFolder.getId()), eq(request));
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

    @Test
    void uploadDocument_success() {
        UUID parentId = UUID.randomUUID();
        String filename = "file.txt";
        Map<String, Object> metadata = Map.of("key", "value");
        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn(filename);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "text/plain");
        when(filePart.headers()).thenReturn(headers);
        when(documentRepository.existsByIdAndType(parentId, FOLDER)).thenReturn(Mono.just(true));
        when(documentRepository.existsByNameAndParentId(filename, parentId)).thenReturn(Mono.just(false));
        when(jsonUtils.toJson(metadata)).thenReturn(Json.of("{}"));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(Document.builder().id(UUID.randomUUID()).name(filename).type(FILE).build()));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        when(storageService.saveFile(filePart)).thenReturn(Mono.just("storage/path"));

        Mono<UploadResponse> result = documentService.uploadDocument(filePart, 123L, parentId, metadata, false, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(resp -> resp.name().equals(filename))
                .verifyComplete();
    }

    @Test
    void uploadDocument_folderNotFound_shouldError() {
        UUID parentId = UUID.randomUUID();
        FilePart filePart = mock(FilePart.class);
        when(filePart.filename()).thenReturn("file.txt");
        when(documentRepository.existsByIdAndType(parentId, FOLDER)).thenReturn(Mono.just(false));

        Mono<UploadResponse> result = documentService.uploadDocument(filePart, 123L, parentId, Map.of(), false, mockAuthentication);

        StepVerifier.create(result)
                .expectError(DocumentNotFoundException.class)
                .verify();
    }

    @Test
    void downloadDocument_success() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder().id(docId).type(FILE).storagePath("path").build();
        when(documentRepository.findById(docId)).thenReturn(Mono.just(doc));
        Resource resource = mock(Resource.class);
        doReturn(Mono.just(resource)).when(storageService).loadFile(anyString());
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class))).thenReturn(Mono.empty());

        Mono<Resource> result = documentService.downloadDocument(docId, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(resource)
                .verifyComplete();
    }

    @Test
    void deleteFiles_notAFile_shouldError() {
        UUID fileId = UUID.randomUUID();
        DeleteRequest request = new DeleteRequest(List.of(fileId));
        Document folder = Document.builder().id(fileId).type(FOLDER).build();
        when(documentRepository.findById(fileId)).thenReturn(Mono.just(folder));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class))).thenReturn(Mono.empty());

        Mono<Void> result = documentService.deleteFiles(request, mockAuthentication);

        StepVerifier.create(result)
                .expectError(OperationForbiddenException.class)
                .verify();
    }

    @Test
    void deleteFolders_success() {
        UUID folderId = UUID.randomUUID();
        DeleteRequest request = new DeleteRequest(List.of(folderId));
        Document folder = Document.builder().id(folderId).type(FOLDER).build();

        when(documentRepository.findById(folderId)).thenReturn(Mono.just(folder));
        when(documentRepository.findByParentIdAndType(folderId, FILE)).thenReturn(Flux.empty());
        when(documentRepository.findByParentIdAndType(folderId, FOLDER)).thenReturn(Flux.empty());
        when(documentRepository.delete(folder)).thenReturn(Mono.empty());
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class))).thenReturn(Mono.empty());

        Mono<Void> result = documentService.deleteFolders(request, mockAuthentication);

        StepVerifier.create(result)
                .verifyComplete();

        verify(documentRepository).delete(folder);
        verify(auditService).logAction(eq("testuser"), eq(DELETE_FOLDER), eq(FOLDER), eq(folderId));
    }

    @Test
    void moveFiles_success() {
        UUID fileId = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();
        MoveRequest request = new MoveRequest(List.of(fileId), targetFolderId, false);
        Document fileToMove = Document.builder().id(fileId).type(FILE).build();
        Document targetFolder = Document.builder().id(targetFolderId).type(FOLDER).build();

        when(documentRepository.findById(fileId)).thenReturn(Mono.just(fileToMove));
        when(documentRepository.findById(targetFolderId)).thenReturn(Mono.just(targetFolder));
        when(documentRepository.existsByNameAndParentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(fileToMove));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());

        Mono<Void> result = documentService.moveFiles(request, mockAuthentication);

        StepVerifier.create(result)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(MOVE_FILE), eq(FILE), eq(fileId), any(List.class));
    }

    @Test
    void moveFolders_success() throws Exception {
        UUID folderId = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();
        MoveRequest request = new MoveRequest(List.of(folderId), targetFolderId, false);
        Document folderToMove = Document.builder().id(folderId).type(FOLDER).build();
        Document targetFolder = Document.builder().id(targetFolderId).type(FOLDER).build();

        when(documentRepository.findById(folderId)).thenReturn(Mono.just(folderToMove));
        when(documentRepository.findById(targetFolderId)).thenReturn(Mono.just(targetFolder));
        when(documentRepository.existsByNameAndParentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(folderToMove));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        //when(documentService.isDescendant(any(), any())).thenReturn(Mono.just(false));

        Mono<Void> result = documentService.moveFolders(request, mockAuthentication);

        StepVerifier.create(result)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(MOVE_FOLDER), eq(FOLDER), eq(folderId), any(List.class));
    }

    @Test
    void copyFiles_success() {
        UUID fileId = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();
        CopyRequest request = new CopyRequest(List.of(fileId), targetFolderId, false);
        Document fileToCopy = Document.builder().id(fileId).name("file-to-copy").type(FILE).storagePath("original-path").build();
        Document targetFolder = Document.builder().id(targetFolderId).type(FOLDER).build();
        Document copiedFile = Document.builder().id(UUID.randomUUID()).name("file-to-copy").type(FILE).storagePath("new-path").build();

        when(documentRepository.findById(fileId)).thenReturn(Mono.just(fileToCopy));
        when(documentRepository.findById(targetFolderId)).thenReturn(Mono.just(targetFolder));
        when(documentRepository.existsByNameAndParentId("file-to-copy", targetFolderId)).thenReturn(Mono.just(false));
        when(storageService.copyFile(anyString())).thenReturn(Mono.just("new-path"));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(copiedFile));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        when(jsonUtils.cloneOrNewEmptyJson(any())).thenReturn(Json.of("{}"));

        Flux<CopyResponse> result = documentService.copyFiles(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(COPY_FILE), eq(FILE), any(UUID.class), any(List.class));
    }

    @Test
    void copyFolders_success() {
        UUID folderId = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();
        CopyRequest request = new CopyRequest(List.of(folderId), targetFolderId, false);
        Document folderToCopy = Document.builder().id(folderId).type(FOLDER).name("folder").build();
        Document targetFolder = Document.builder().id(targetFolderId).type(FOLDER).build();
        Document copiedFolder = Document.builder().id(UUID.randomUUID()).type(FOLDER).build();

        when(documentRepository.findById(folderId)).thenReturn(Mono.just(folderToCopy));
        when(documentRepository.findById(targetFolderId)).thenReturn(Mono.just(targetFolder));
        when(documentRepository.existsByNameAndParentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.existsByIdAndType(any(), any())).thenReturn(Mono.just(true));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(copiedFolder));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        when(jsonUtils.cloneOrNewEmptyJson(any())).thenReturn(Json.of("{}"));
        when(documentRepository.findByParentIdAndType(any(), any(DocumentType.class))).thenReturn(Flux.empty());

        Flux<UUID> result = documentService.copyFolders(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(documentRepository, times(1)).save(any(Document.class));
        verify(auditService, times(1)).logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class));
    }

    @Test
    void renameFile_success() {
        UUID fileId = UUID.randomUUID();
        RenameRequest request = new RenameRequest("new-name.txt");
        Document fileToRename = Document.builder().id(fileId).type(FILE).name("old-name.txt").build();

        when(documentRepository.findById(fileId)).thenReturn(Mono.just(fileToRename));
        when(documentRepository.existsByNameAndParentIdIsNull(any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(fileToRename));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());

        Mono<Document> result = documentService.renameFile(fileId, request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(doc -> doc.getName().equals("new-name.txt"))
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(RENAME_FILE), eq(FILE), eq(fileId), any(List.class));
    }

    @Test
    void renameFolder_success() {
        UUID folderId = UUID.randomUUID();
        RenameRequest request = new RenameRequest("new-name");
        Document folderToRename = Document.builder().id(folderId).parentId(UUID.randomUUID()).type(FOLDER).name("old-name").build();

        when(documentRepository.findById(folderId)).thenReturn(Mono.just(folderToRename));
        when(documentRepository.existsByNameAndParentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(folderToRename));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());

        Mono<Document> result = documentService.renameFolder(folderId, request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextMatches(doc -> doc.getName().equals("new-name"))
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(RENAME_FOLDER), eq(FOLDER), eq(folderId), any(List.class));
    }

    @Test
    void replaceDocumentContent_success() {
        UUID documentId = UUID.randomUUID();
        FilePart newFilePart = mock(FilePart.class);
        Document document = Document.builder().id(documentId).type(FILE).storagePath("old-path").build();

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        when(storageService.saveFile(newFilePart)).thenReturn(Mono.just("new-path"));
        when(storageService.deleteFile("old-path")).thenReturn(Mono.empty());
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(document));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        Mockito.lenient().when(newFilePart.content()).thenReturn(Flux.empty());
        Mockito.lenient().when(newFilePart.headers()).thenReturn(new HttpHeaders());

        Mono<Document> result = documentService.replaceDocumentContent(documentId, newFilePart, 123L, mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(storageService).deleteFile("old-path");
        verify(auditService).logAction(eq("testuser"), eq(REPLACE_DOCUMENT_CONTENT), eq(FILE), eq(documentId), any(List.class));
    }

    @Test
    void replaceDocumentMetadata_success() {
        UUID documentId = UUID.randomUUID();
        Map<String, Object> newMetadata = Map.of("key", "value");
        Document document = Document.builder().id(documentId).type(FILE).build();

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(document));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        ObjectNode node = JsonNodeFactory.instance.objectNode().put("key", "value");
        when(objectMapper.valueToTree(newMetadata)).thenReturn(node);

        Mono<Document> result = documentService.replaceDocumentMetadata(documentId, newMetadata, mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(REPLACE_DOCUMENT_METADATA), any(DocumentType.class), eq(documentId), any(List.class));
    }

    @Test
    void updateDocumentMetadata_success() {
        UUID documentId = UUID.randomUUID();
        Map<String, Object> map = Map.of("key", "value");
        UpdateMetadataRequest request = new UpdateMetadataRequest(map);
        Document document = Document.builder().id(documentId).type(FILE).build();

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(document));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        when(jsonUtils.toJsonNode(any())).thenReturn(new ObjectMapper().createObjectNode());
        when(objectMapper.valueToTree(any())).thenReturn(JsonNodeFactory.instance.objectNode().textNode("value"));

        Mono<Document> result = documentService.updateDocumentMetadata(documentId, request, mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(UPDATE_DOCUMENT_METADATA), any(DocumentType.class), eq(documentId), any(List.class));
    }

    @Test
    void deleteDocumentMetadata_success() {
        UUID documentId = UUID.randomUUID();
        DeleteMetadataRequest request = new DeleteMetadataRequest(List.of("key"));
        Document document = Document.builder().id(documentId).type(FILE).metadata(Json.of("{\"key\":\"value\"}")).build();

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(document));
        when(auditService.logAction(anyString(), any(AuditAction.class), any(DocumentType.class), any(UUID.class), any(List.class))).thenReturn(Mono.empty());
        when(jsonUtils.toMap(any())).thenReturn(new ObjectMapper().convertValue(Map.of("key", "value"), Map.class));
        when(jsonUtils.toJson(any(Map.class))).thenReturn(Json.of("{}"));

        Mono<Void> result = documentService.deleteDocumentMetadata(documentId, request, mockAuthentication);

        StepVerifier.create(result)
                .verifyComplete();

        verify(documentRepository).save(any(Document.class));
        verify(auditService).logAction(eq("testuser"), eq(DELETE_DOCUMENT_METADATA), any(DocumentType.class), eq(documentId), any(List.class));
    }

    @Test
    void downloadMultipleDocumentsAsZip_success() throws InterruptedException {
        UUID docId1 = UUID.randomUUID();
        UUID docId2 = UUID.randomUUID();
        Document doc1 = Document.builder().id(docId1).type(FILE).name("file1.txt").storagePath("path1").build();
        Document doc2 = Document.builder().id(docId2).type(FILE).name("file2.txt").storagePath("path2").build();

        when(documentRepository.findByIdIn(List.of(docId1, docId2))).thenReturn(Flux.just(doc1, doc2));
        Resource resource = mock(Resource.class);
        Mockito.lenient().doReturn(Mono.just(resource)).when(storageService).loadFile(anyString());
        Mockito.lenient().when(resource.exists()).thenReturn(true);

        Mono<Resource> result = documentService.downloadMultipleDocumentsAsZip(List.of(docId1, docId2), mockAuthentication);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void searchDocumentIdsByMetadata_success() throws JsonProcessingException {
        SearchByMetadataRequest request = new SearchByMetadataRequest(null, null, null, null, Map.of("key", "value"));
        UUID docId = UUID.randomUUID();

        when(documentDAO.listDocumentIds(any())).thenReturn(Flux.just(docId));

        Flux<UUID> result = documentService.searchDocumentIdsByMetadata(request, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(docId)
                .verifyComplete();
    }

    @Test
    void getDocumentMetadata_success() throws JsonProcessingException {
        UUID documentId = UUID.randomUUID();
        SearchMetadataRequest request = new SearchMetadataRequest(null);
        String jsonAsString = "{\"key\":\"value\"}";
        Document document = Document.builder().id(documentId).metadata(Json.of(jsonAsString)).build();
        Map<String, Object> metadata = Map.of("key", "value");

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));
        ObjectNode node = JsonNodeFactory.instance.objectNode().put("key", "value");
        when(jsonUtils.toJsonNode(any())).thenReturn(node);
        when(objectMapper.convertValue(node, Map.class)).thenReturn(Map.of("key", "value"));

        Mono<Map<String, Object>> result = documentService.getDocumentMetadata(documentId, request, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(metadata)
                .verifyComplete();
    }

    @Test
    void getDocumentInfo_success() {
        UUID documentId = UUID.randomUUID();
        Document document = Document.builder().id(documentId).type(FILE).name("test.txt").build();
        DocumentInfo documentInfo = new DocumentInfo(FILE, "test.txt", null, null, null);

        when(documentRepository.findById(documentId)).thenReturn(Mono.just(document));

        Mono<DocumentInfo> result = documentService.getDocumentInfo(documentId, false, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(documentInfo)
                .verifyComplete();
    }

    @Test
    void listFolderInfo_success() {
        UUID folderId = UUID.randomUUID();
        FolderElementInfo folderElementInfo = new FolderElementInfo(UUID.randomUUID(), FILE, "test.txt");

        when(documentRepository.existsByIdAndType(folderId, FOLDER)).thenReturn(Mono.just(true));
        when(documentRepository.listDocumentInfoInFolder(folderId)).thenReturn(Flux.just(folderElementInfo));

        Flux<FolderElementInfo> result = documentService.listFolderInfo(folderId, false, false, mockAuthentication);

        StepVerifier.create(result)
                .expectNext(folderElementInfo)
                .verifyComplete();
    }
}