package org.openfilz.dms.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.openfilz.dms.dto.*;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.AuditAction;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.exception.DocumentNotFoundException;
import org.openfilz.dms.exception.DuplicateNameException;
import org.openfilz.dms.exception.OperationForbiddenException;
import org.openfilz.dms.exception.StorageException;
import org.openfilz.dms.repository.DocumentDAO;
import org.openfilz.dms.repository.DocumentRepository;
import org.openfilz.dms.service.AuditService;
import org.openfilz.dms.service.DocumentService;
import org.openfilz.dms.service.StorageService;
import org.openfilz.dms.utils.JsonUtils;
import org.openfilz.dms.utils.MapEntry;
import org.openfilz.dms.utils.UserPrincipalExtractor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.openfilz.dms.enums.AuditAction.*;
import static org.openfilz.dms.enums.DocumentType.FILE;
import static org.openfilz.dms.enums.DocumentType.FOLDER;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper; // For JSONB processing
    private final AuditService auditService; // For auditing
    private final JsonUtils jsonUtils;
    private final DocumentDAO documentDAO;


    @Override
    @Transactional // Ensure R2DBC @Transactional is properly configured if complex operations span DB and FS
    public Mono<FolderResponse> createFolder(CreateFolderRequest request, Authentication auth) {
        if (request.name().contains(StorageService.FOLDER_SEPARATOR)) {
            return Mono.error(new OperationForbiddenException("Folder name should not contains any '/'"));
        }
        return UserPrincipalExtractor.getConnectedUser(auth)
                .flatMap(username -> doCreateFolder(request, username, null, false, null))
                .flatMap(savedFolder -> Mono.just(new FolderResponse(savedFolder.getId(), savedFolder.getName(), savedFolder.getParentId())));
    }

    private Mono<Document> doCreateFolder(CreateFolderRequest request, String username, Json folderMetadata,
                                          boolean copy, List<MapEntry> auditDetails) {
        log.debug("doCreateFolder folder {}", request);
        return Mono.just(username).flatMap(_ -> documentExists(request.name(), request.parentId()))
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateNameException(FOLDER, request.name()));
                    }
                    if(request.parentId() != null) {
                        return documentRepository.existsByIdAndType(request.parentId(), FOLDER).flatMap(folderExists->{
                           if(!folderExists) {
                               return Mono.error(new DocumentNotFoundException(FOLDER, request.parentId()));
                           }
                           return saveFolderInRepository(request, username, folderMetadata);
                        });
                    }
                    return saveFolderInRepository(request, username, folderMetadata);
                }).flatMap(savedFolder -> {
                    if (auditDetails == null) {
                        return auditService.logAction(username, copy ? AuditAction.COPY_FOLDER : AuditAction.CREATE_FOLDER, FOLDER, savedFolder.getId(), request)
                                .thenReturn(savedFolder);
                    }
                    return auditService.logAction(username, copy ? AuditAction.COPY_FOLDER : AuditAction.CREATE_FOLDER, FOLDER, savedFolder.getId(), auditDetails)
                            .thenReturn(savedFolder);
                });

    }

    private Mono<Document> saveFolderInRepository(CreateFolderRequest request, String username, Json folderMetadata) {
        Document folder = Document.builder()
                .name(request.name())
                .type(DocumentType.FOLDER)
                .parentId(request.parentId())
                .metadata(folderMetadata == null ? jsonUtils.emptyJson() : folderMetadata) // Empty JSON object
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(username)
                .updatedBy(username)
                .build();
        return documentRepository.save(folder);
    }

    private Mono<Boolean> documentExists(String documentName, UUID parentFolderId) {
        return parentFolderId == null ?
                documentExistsAtRootLevel(documentName) :
                documentRepository.existsByNameAndParentId(documentName, parentFolderId);
    }

    private Mono<Boolean> documentExistsAtRootLevel(String documentName) {
        return documentRepository.existsByNameAndParentIdIsNull(documentName);
    }

    @Override
    @Transactional
    public Mono<UploadResponse> uploadDocument(FilePart filePart, Long contentLength, UUID parentFolderId, Map<String, Object> metadata, Boolean allowDuplicateFileNames, Authentication auth) {
        String originalFilename = filePart.filename().replace(StorageService.FILENAME_SEPARATOR, "");
        return UserPrincipalExtractor.getConnectedUser(auth)
                .flatMap(username -> {
                    if (parentFolderId != null) {
                        return documentRepository.existsByIdAndType(parentFolderId, FOLDER)
                                .flatMap(exists -> {
                                    if (!exists) {
                                        return Mono.error(new DocumentNotFoundException(FOLDER, parentFolderId));
                                    }
                                    return doUploadDocument(filePart, contentLength, parentFolderId, metadata, originalFilename, allowDuplicateFileNames, username);
                                });
                    }
                    return doUploadDocument(filePart, contentLength, null, metadata, originalFilename, allowDuplicateFileNames, username);
                });
    }

    private Mono<UploadResponse> doUploadDocument(FilePart filePart, Long contentLength, UUID parentFolderId, Map<String, Object> metadata, String originalFilename, Boolean allowDuplicateFileNames, String username) {
        if(allowDuplicateFileNames) {
            return storageService.saveFile(filePart)
                    .flatMap(storagePath -> saveDocumentInDatabase(filePart, contentLength, parentFolderId, metadata, originalFilename, username, storagePath))
                    .flatMap(savedDoc -> auditUploadActionAndReturnResponse(parentFolderId, metadata, username, savedDoc));
        }
        Mono<Boolean> duplicateCheck = documentExists(originalFilename, parentFolderId);
        return duplicateCheck.flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateNameException(FILE, originalFilename));
                    }
                    return storageService.saveFile(filePart);
                })
                .flatMap(storagePath -> saveDocumentInDatabase(filePart, contentLength, parentFolderId, metadata, originalFilename, username, storagePath))
                .flatMap(savedDoc -> auditUploadActionAndReturnResponse(parentFolderId, metadata, username, savedDoc));
    }

    private Mono<UploadResponse> auditUploadActionAndReturnResponse(UUID parentFolderId, Map<String, Object> metadata, String username, Document savedDoc) {
        return auditService.logAction(username, AuditAction.UPLOAD_DOCUMENT, FILE, savedDoc.getId(),
                        List.of(new MapEntry("filename", savedDoc.getName()), new MapEntry("parentId", parentFolderId), new MapEntry("metadata", metadata)))
                .thenReturn(new UploadResponse(savedDoc.getId(), savedDoc.getName(), savedDoc.getContentType(), savedDoc.getSize()));
    }


    private Mono<Document> saveDocumentInDatabase(FilePart filePart, Long contentLength, UUID parentFolderId, Map<String, Object> metadata, String originalFilename, String username, String storagePath) {
        if(contentLength == null) {
            return storageService.getFileLength(storagePath)
                    .flatMap(fileLength -> saveDocumentInDB(filePart, storagePath, fileLength, parentFolderId, metadata, originalFilename, username));
        }
        return saveDocumentInDB(filePart, storagePath, contentLength, parentFolderId, metadata, originalFilename, username);
    }

    private Mono<Document> saveDocumentInDB(FilePart filePart, String storagePath, Long contentLength, UUID parentFolderId, Map<String, Object> metadata, String originalFilename, String username) {
        Document document = Document.builder()
                .name(originalFilename)
                .type(FILE)
                .contentType(filePart.headers().getContentType() != null ? filePart.headers().getContentType().toString() : APPLICATION_OCTET_STREAM)
                .size(contentLength)
                .parentId(parentFolderId)
                .storagePath(storagePath)
                .metadata(jsonUtils.toJson(metadata))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .createdBy(username)
                .updatedBy(username)
                .build();
        return documentRepository.save(document);
    }

    @Override
    @Transactional
    public Mono<Resource> downloadDocument(UUID documentId, Authentication auth) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .filter(doc -> doc.getType() == FILE) // Ensure it's a file
                .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot download a folder directly. ID: " + documentId)))
                .flatMap(document -> storageService.loadFile(document.getStoragePath()))
                .flatMap(r -> UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> auditService.logAction(username, AuditAction.DOWNLOAD_DOCUMENT, FILE, documentId)).thenReturn(r));
    }


    @Override
    @Transactional
    public Mono<Void> deleteFiles(DeleteRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> Flux.fromIterable(request.documentIds())
                .flatMap(docId -> documentRepository.findById(docId)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(docId)))
                        .filter(doc -> doc.getType() == FILE) // Ensure it's a file
                        .switchIfEmpty(Mono.error(new OperationForbiddenException("ID " + docId + " is a folder. Use delete folders API.")))
                        .flatMap(document -> storageService.deleteFile(document.getStoragePath())
                                .then(documentRepository.delete(document)))
                        .then(auditService.logAction(username, AuditAction.DELETE_FILE, FILE, docId))
                )
                .then());
    }


    @Override
    @Transactional
    public Mono<Void> deleteFolders(DeleteRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> Flux.fromIterable(request.documentIds())
                .flatMap(folderId -> deleteFolderRecursive(folderId, username))
                .then()
        );
    }

    private Mono<Void> deleteFolderRecursive(UUID folderId, String username) {
        return documentRepository.findById(folderId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, folderId)))
                .filter(doc -> doc.getType() == DocumentType.FOLDER)
                .switchIfEmpty(Mono.error(new OperationForbiddenException("Not a folder: " + folderId)))
                .flatMap(folder -> {
                    // 1. Delete child files
                    Mono<Void> deleteChildFiles = documentRepository.findByParentIdAndType(folderId, FILE)
                            .flatMap(file -> storageService.deleteFile(file.getStoragePath())
                                    .then(documentRepository.delete(file))
                                    .then(auditService.logAction(username, DELETE_FILE_CHILD, FILE, file.getId(), List.of(new MapEntry("parentFolderId", folderId))))
                            ).then();

                    // 2. Recursively delete child folders
                    Mono<Void> deleteChildFolders = documentRepository.findByParentIdAndType(folderId, DocumentType.FOLDER)
                            .flatMap(childFolder -> deleteFolderRecursive(childFolder.getId(), username))
                            .then();

                    // 3. Delete the folder itself from DB (and storage if it had a physical representation)
                    return Mono.when(deleteChildFiles, deleteChildFolders)
                            .then(documentRepository.delete(folder))
                            .then(auditService.logAction(username, AuditAction.DELETE_FOLDER, FOLDER, folderId));
                });
    }


    @Override
    @Transactional
    public Mono<Void> moveFiles(MoveRequest request, Authentication auth) {
        if(request.targetFolderId() == null) {
            return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username ->moveFiles(request, username));
        }

        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> {
            // 1. Validate target folder exists and is a folder
            Mono<Document> targetFolderMono = documentRepository.findById(request.targetFolderId())
                    .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, request.targetFolderId())))
                    .filter(doc -> doc.getType() == DocumentType.FOLDER)
                    .switchIfEmpty(Mono.error(new OperationForbiddenException("Target is not a folder: " + request.targetFolderId())));

            return targetFolderMono.flatMap(_ ->
                    moveFiles(request, username)
            );
        });
    }

    private Mono<Void> moveFiles(MoveRequest request, String username) {
        return Flux.fromIterable(request.documentIds())
                .flatMap(fileId -> documentRepository.findById(fileId)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(FILE, fileId)))
                        .filter(doc -> doc.getType() == FILE) // Ensure it's a file being moved
                        .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot move folder using file move API: " + fileId)))
                        .flatMap(fileToMove -> {
                            // Check for name collision in target folder
                            return moveDocument(request, username, fileToMove)
                                    .flatMap(movedFile -> auditService.logAction(username, MOVE_FILE, FILE, movedFile.getId(),
                                            List.of(new MapEntry("targetFolderId", request.targetFolderId()))));
                        })
                )
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> moveFolders(MoveRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> {
            Mono<Document> targetFolderMono = documentRepository.findById(request.targetFolderId())
                    .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, request.targetFolderId())))
                    .filter(doc -> doc.getType() == DocumentType.FOLDER)
                    .switchIfEmpty(Mono.error(new OperationForbiddenException("Target is not a folder: " + request.targetFolderId())));

            return targetFolderMono.flatMap(targetFolder ->
                    Flux.fromIterable(request.documentIds())
                            .flatMap(folderIdToMove -> {
                                if (folderIdToMove.equals(request.targetFolderId())) {
                                    return Mono.error(new OperationForbiddenException("Cannot move a folder into itself."));
                                }
                                // Check for moving a parent into its child (cycle) - more complex check needed for full hierarchy
                                Mono<Boolean> isMovingToDescendant = isDescendant(request.targetFolderId(), folderIdToMove);

                                return isMovingToDescendant.flatMap(isDescendant -> {
                                    if (isDescendant) {
                                        return Mono.error(new OperationForbiddenException("Cannot move a folder into one of its descendants."));
                                    }

                                    return documentRepository.findById(folderIdToMove)
                                            .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, folderIdToMove)))
                                            .filter(doc -> doc.getType() == DocumentType.FOLDER)
                                            .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot move file using folder move API: " + folderIdToMove)))
                                            .flatMap(folderToMove -> {
                                                // Check for name collision in target folder
                                                return moveDocument(request, username, folderToMove)
                                                        .flatMap(movedFolder -> auditService.logAction(username, MOVE_FOLDER, FOLDER, movedFolder.getId(),
                                                                List.of(new MapEntry("targetFolderId", request.targetFolderId()))));
                                            });
                                });
                            })
                            .then()
            );
        });
    }

    private Mono<Document> moveDocument(MoveRequest request, String username, Document documentToMove) {
        if(documentToMove.getParentId() == request.targetFolderId()) {
            return Mono.error(new DuplicateNameException("Impossible to move a document in the same folder : you may want to use /copy instead"));
        }
        if(request.allowDuplicateFileNames() != null && request.allowDuplicateFileNames()) {
            return doMoveDocuments(request, username, documentToMove);
        }
        return documentRepository.existsByNameAndParentId(documentToMove.getName(), request.targetFolderId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DuplicateNameException(
                                "A file/folder with name '" + documentToMove.getName() + "' already exists in the target folder."));
                    }
                    return doMoveDocuments(request, username, documentToMove);
                });
    }

    private Mono<Document> doMoveDocuments(MoveRequest request, String username, Document documentToMove) {
        documentToMove.setParentId(request.targetFolderId());
        documentToMove.setUpdatedAt(OffsetDateTime.now());
        documentToMove.setUpdatedBy(username);
        return documentRepository.save(documentToMove);
    }

    // Helper to check if 'potentialChildId' is a descendant of 'potentialParentId'
    private Mono<Boolean> isDescendant(UUID potentialChildId, UUID potentialParentId) {
        if (potentialChildId == null || potentialParentId == null) {
            return Mono.just(false);
        }
        if (potentialChildId.equals(potentialParentId)) { // A folder cannot be its own descendant in this context.
            return Mono.just(true); // Or false depending on definition, for move, this is an invalid state
        }

        return documentRepository.findById(potentialChildId)
                .flatMap(childDoc -> {
                    if (childDoc.getParentId() == null) {
                        return Mono.just(false); // Reached root without finding potentialParentId
                    }
                    if (childDoc.getParentId().equals(potentialParentId)) {
                        return Mono.just(true); // Found potentialParentId as direct parent
                    }
                    return isDescendant(childDoc.getParentId(), potentialParentId); // Recurse up
                })
                .defaultIfEmpty(false); // Child not found, so not a descendant
    }


    @Override
    @Transactional
    public Flux<CopyResponse> copyFiles(CopyRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMapMany(username -> {
            if (request.targetFolderId() == null) {
                return doCopyFiles(request, username);
            }
            Mono<Document> targetFolderMono = documentRepository.findById(request.targetFolderId())
                    .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, request.targetFolderId())))
                    .filter(doc -> doc.getType() == DocumentType.FOLDER)
                    .switchIfEmpty(Mono.error(new OperationForbiddenException("Target is not a folder: " + request.targetFolderId())));

            return targetFolderMono.flatMapMany(_ ->
                    doCopyFiles(request, username)
            );
        });
    }

    private Flux<CopyResponse> doCopyFiles(CopyRequest request, String username) {
        return Flux.fromIterable(request.documentIds())
                .flatMapSequential(fileIdToCopy -> documentRepository.findById(fileIdToCopy)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(FILE, fileIdToCopy)))
                        .filter(doc -> doc.getType() == FILE)
                        .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot copy folder using file copy API: " + fileIdToCopy)))
                        .flatMap(originalFile -> raiseErrorIfExists(originalFile.getName(), request.targetFolderId(), request.allowDuplicateFileNames())
                                .flatMap(filename -> storageService.copyFile(originalFile.getStoragePath())
                                        .flatMap(newStoragePath -> {
                                            // 2. Create new DB entry for the copied file
                                            Document copiedFile = Document.builder()
                                                    .name(originalFile.getName()) // Handle potential name collision, e.g., "file (copy).txt"
                                                    .type(FILE)
                                                    .contentType(originalFile.getContentType())
                                                    .size(originalFile.getSize())
                                                    .parentId(request.targetFolderId())
                                                    .storagePath(newStoragePath)
                                                    .metadata(jsonUtils.cloneOrNewEmptyJson(originalFile.getMetadata()))
                                                    .createdAt(OffsetDateTime.now())
                                                    .updatedAt(OffsetDateTime.now())
                                                    .createdBy(username)
                                                    .updatedBy(username)
                                                    .build();
                                            return documentRepository.save(copiedFile);
                                        })
                                        .flatMap(cf -> auditService.logAction(username, COPY_FILE, FILE, cf.getId(),
                                                        List.of(new MapEntry("sourceFileId", fileIdToCopy), new MapEntry("targetFolderId", request.targetFolderId()), new MapEntry("copîedFileId", cf.getId())))
                                                .thenReturn(new CopyResponse(fileIdToCopy, cf.getId()))))
                        )
                );
    }


    // check if alrerady exists in DB
    private Mono<String> raiseErrorIfExists(String originalName, UUID parentId, Boolean allowDuplicateFileNames) {
        if(allowDuplicateFileNames != null && allowDuplicateFileNames) {
            return Mono.just(originalName);
        }
        Mono<Boolean> existsCheck = documentExists(originalName, parentId);

        return existsCheck.flatMap(exists -> {
            if (exists) {
                return Mono.error(new DuplicateNameException(FOLDER, originalName));
            }
            return Mono.just(originalName);
        });

        // This is a simplified loop for demonstration. A fully reactive loop is more complex.
        // For a true reactive approach, you'd use expand or a similar operator.
        // This blocking approach is NOT ideal in a reactive service.
       /* while (existsCheck.blockOptional().orElse(false)) { // DANGER: BLOCKING
            currentName = baseName + " (" + count++ + ")" + (extension != null && !extension.isEmpty() ? "." + extension : "");
            existsCheck = (parentId == null)
                ? documentRepository.existsByNameAndParentIdIsNull(currentName)
                : documentRepository.existsByNameAndParentId(currentName, parentId);
        }
        return Mono.just(currentName);*/

        // Proper reactive way (conceptual):
        /*
        return Flux.defer(() -> {
            final int[] copyCount = {0}; // Effectively final for lambda
            final String[] nameToTest = {originalName};

            return Mono.defer(() -> parentId == null ?
                    documentRepository.existsByNameAndParentIdIsNull(nameToTest[0]) :
                    documentRepository.existsByNameAndParentId(nameToTest[0], parentId)
                )
                .expand(exists -> {
                    if (!exists) {
                        return Mono.empty(); // Found a unique name
                    }
                    copyCount[0]++;
                    nameToTest[0] = baseName + " (" + copyCount[0] + ")" + (StringUtils.hasText(extension) ? "." + extension : "");
                    return parentId == null ?
                           documentRepository.existsByNameAndParentIdIsNull(nameToTest[0]) :
                           documentRepository.existsByNameAndParentId(nameToTest[0], parentId);
                })
                .filter(exists -> !exists) // Take the first non-existent check (which means name is unique)
                .next() // Should emit one item (the last 'false' from exists check)
                .thenReturn(nameToTest[0]); // Return the unique name
        });
        */
    }


    @Override
    @Transactional
    public Flux<UUID> copyFolders(CopyRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMapMany(username -> {
            Mono<Document> targetFolderMono = documentRepository.findById(request.targetFolderId())
                    .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, request.targetFolderId())))
                    .filter(doc -> doc.getType() == DocumentType.FOLDER)
                    .switchIfEmpty(Mono.error(new OperationForbiddenException("Target is not a folder: " + request.targetFolderId())));

            return targetFolderMono.flatMapMany(_ ->
                    Flux.fromIterable(request.documentIds())
                            .concatMap(folderIdToCopy -> {
                                if (folderIdToCopy.equals(request.targetFolderId())) {
                                    return Flux.error(new OperationForbiddenException("Cannot copy a folder into itself."));
                                }
                                // Add check for copying a parent into its child if needed, similar to move
                                return copyFolderRecursive(folderIdToCopy, request.targetFolderId(), request.allowDuplicateFileNames(), username);
                            })
            );
        });
    }

    private Flux<UUID> copyFolderRecursive(UUID sourceFolderId, UUID targetParentFolderId, Boolean allowDuplicateFileNames, String username) {
        return documentRepository.findById(sourceFolderId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, sourceFolderId)))
                .flatMapMany(sourceFolder -> {
                    // Generate unique name for the new folder in the target location
                    return raiseErrorIfExists(sourceFolder.getName(), targetParentFolderId, allowDuplicateFileNames)
                            .flatMap(__ -> doCreateFolder(new CreateFolderRequest(sourceFolder.getName(), targetParentFolderId), username,
                                    jsonUtils.cloneOrNewEmptyJson(sourceFolder.getMetadata()), true, List.of(new MapEntry("sourceFolderId",
                                            sourceFolderId), new MapEntry("targetParentFolderId", targetParentFolderId))))
                            .flatMap(savedNewFolder -> {
                                UUID newFolderId = savedNewFolder.getId();

                                // 2. Copy child files
                                Flux<Document> copyChildFiles = documentRepository.findByParentIdAndType(sourceFolderId, FILE)
                                        .flatMap(childFile -> {
                                            // For each child file, copy physical file and create DB entry under newFolderId
                                            return raiseErrorIfExists(childFile.getName(), newFolderId, allowDuplicateFileNames)
                                                    .flatMap(__ -> storageService.copyFile(childFile.getStoragePath())
                                                            .flatMap(newChildFileName -> {
                                                                Document copiedChildFile = Document.builder()
                                                                        .name(childFile.getName())
                                                                        .type(FILE)
                                                                        .contentType(childFile.getContentType())
                                                                        .size(childFile.getSize())
                                                                        .parentId(newFolderId)
                                                                        .storagePath(newChildFileName)
                                                                        .metadata(jsonUtils.cloneOrNewEmptyJson(childFile.getMetadata()))
                                                                        .createdAt(OffsetDateTime.now())
                                                                        .updatedAt(OffsetDateTime.now())
                                                                        .createdBy(username)
                                                                        .updatedBy(username)
                                                                        .build();
                                                                return documentRepository.save(copiedChildFile);
                                                            })
                                                            .flatMap(ccf -> auditService.logAction(username, COPY_FILE_CHILD, FILE, ccf.getId(),
                                                                    List.of(new MapEntry("sourceFileId", childFile.getId()), new MapEntry("targetParentFolderId", newFolderId))).thenReturn(ccf)));
                                        });

                                // 3. Recursively copy child folders
                                Flux<UUID> copyChildSubFolders = documentRepository.findByParentIdAndType(sourceFolderId, FOLDER)
                                        .flatMap(childSubFolder -> copyFolderRecursive(childSubFolder.getId(), newFolderId, allowDuplicateFileNames, username));

                                return Mono.when(copyChildFiles, copyChildSubFolders).thenReturn(newFolderId);
                            });
                });
    }


    @Override
    @Transactional
    public Mono<Document> renameFile(UUID fileId, RenameRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> documentRepository.findById(fileId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(FILE, fileId)))
                .filter(doc -> doc.getType() == FILE)
                .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot rename folder using file rename API: " + fileId)))
                .flatMap(fileToRename -> {
                    // Check for name collision in its current parent folder
                    Mono<Boolean> duplicateCheck = documentExists(request.newName(), fileToRename.getParentId());
                    return saveFileToRename(request, username, fileToRename, duplicateCheck);
                })
                .flatMap(renamedFile -> auditService.logAction(username, RENAME_FILE, FILE, renamedFile.getId(),
                        List.of(new MapEntry("newName", request.newName()))).thenReturn(renamedFile)));
    }

    private Mono<Document> saveFileToRename(RenameRequest request, String username, Document fileToRename, Mono<Boolean> duplicateCheck) {
        return duplicateCheck.flatMap(exists -> {
            if (exists) {
                return Mono.error(new DuplicateNameException(
                        "A file/folder with name '" + request.newName() + "' already exists in the current location."));
            }
            fileToRename.setName(request.newName());
            fileToRename.setUpdatedAt(OffsetDateTime.now());
            fileToRename.setUpdatedBy(username);
            // Note: Physical file name on storage is NOT changed here for simplicity.
            // If physical rename is needed, storageService.renameFile(oldPath, newPath) would be called.
            // And document.storagePath would need an update if it includes the name.
            return documentRepository.save(fileToRename);
        });
    }

    @Override
    @Transactional
    public Mono<Document> renameFolder(UUID folderId, RenameRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> documentRepository.findById(folderId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(FOLDER, folderId)))
                .filter(doc -> doc.getType() == DocumentType.FOLDER)
                .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot rename file using folder rename API: " + folderId)))
                .flatMap(folderToRename -> {
                    if (folderToRename.getName().equals(request.newName())) {
                        return Mono.error(new DuplicateNameException("The folder has already the name provided"));
                    }
                    Mono<Boolean> duplicateCheck = documentExists(request.newName(), folderToRename.getParentId());

                    return saveFileToRename(request, username, folderToRename, duplicateCheck);
                })
                .flatMap(renamedFolder -> auditService.logAction(username, RENAME_FOLDER, FOLDER, renamedFolder.getId(),
                        List.of(new MapEntry("newName", request.newName()))).thenReturn(renamedFolder)));
    }


    @Override
    @Transactional
    public Mono<Document> replaceDocumentContent(UUID documentId, FilePart newFilePart, Long contentLength, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .filter(doc -> doc.getType() == FILE) // Only files have content to replace
                .switchIfEmpty(Mono.error(new OperationForbiddenException("Cannot replace content of a folder: " + documentId)))
                .flatMap(document -> {
                    // 1. Save new file content
                    String oldStoragePath = document.getStoragePath();

                    return storageService.saveFile(newFilePart)
                            .flatMap(newStoragePath ->
                                    replaceDocumentContentAndSave(newFilePart, contentLength, username, document, newStoragePath, oldStoragePath));
                }));
    }

    private Mono<Document> replaceDocumentContentAndSave(FilePart newFilePart, Long contentLength, String username, Document document, String newStoragePath, String oldStoragePath) {
        if(contentLength == null) {
            return storageService.getFileLength(newStoragePath)
                    .flatMap(fileLength -> replaceDocumentInDB(newFilePart, newStoragePath, oldStoragePath, fileLength, username, document));
        }
        return replaceDocumentInDB(newFilePart, newStoragePath, oldStoragePath, contentLength, username, document);
    }

    private Mono<Document> replaceDocumentInDB(FilePart newFilePart, String newStoragePath, String oldStoragePath, Long contentLength, String username, Document document) {
        document.setStoragePath(newStoragePath);
        document.setContentType(newFilePart.headers().getContentType() != null ? newFilePart.headers().getContentType().toString() : APPLICATION_OCTET_STREAM);
        document.setUpdatedAt(OffsetDateTime.now());
        document.setUpdatedBy(username);
        document.setSize(contentLength);
        return documentRepository.save(document)
                .flatMap(savedDoc -> {
                    // 3. Delete old file content from storage
                    if (oldStoragePath != null && !oldStoragePath.equals(newStoragePath)) {
                        return storageService.deleteFile(oldStoragePath).thenReturn(savedDoc);
                    }
                    return Mono.just(savedDoc);
                })
                .flatMap(updatedDoc -> auditService.logAction(username, REPLACE_DOCUMENT_CONTENT, FILE, updatedDoc.getId(),
                        List.of(new MapEntry("newFileName", newFilePart.filename()))))
                .thenReturn(document);
    }

    @Override
    @Transactional
    public Mono<Document> replaceDocumentMetadata(UUID documentId, Map<String, Object> newMetadata, Authentication auth) {

        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username ->
                documentRepository.findById(documentId)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                        .flatMap(document -> {
                            try {
                                document.setMetadata(Json.of(objectMapper.valueToTree(newMetadata != null ? newMetadata : new HashMap<>()).toString()));
                                document.setUpdatedAt(OffsetDateTime.now());
                                document.setUpdatedBy(username);
                                return documentRepository.save(document);
                            } catch (Exception e) {
                                return Mono.error(new RuntimeException("Error processing metadata for replacement: " + e.getMessage()));
                            }
                        })
                        .flatMap(updatedDoc -> auditService.logAction(username, REPLACE_DOCUMENT_METADATA, updatedDoc.getType(), updatedDoc.getId(),
                                List.of(new MapEntry("newMetadata", newMetadata))).thenReturn(updatedDoc)));
    }

    @Override
    @Transactional
    public Mono<Document> updateDocumentMetadata(UUID documentId, UpdateMetadataRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(document -> {
                    JsonNode currentMetadata = jsonUtils.toJsonNode(document.getMetadata());
                    ObjectNode updatedMetadataNode;

                    if (currentMetadata == null || currentMetadata.isNull() || !currentMetadata.isObject()) {
                        updatedMetadataNode = objectMapper.createObjectNode();
                    } else {
                        updatedMetadataNode = (ObjectNode) currentMetadata.deepCopy();
                    }

                    for (Map.Entry<String, Object> entry : request.metadataToUpdate().entrySet()) {
                        updatedMetadataNode.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
                    }
                    document.setMetadata(jsonUtils.toJson(updatedMetadataNode));
                    document.setUpdatedAt(OffsetDateTime.now());
                    document.setUpdatedBy(username);
                    return documentRepository.save(document);
                })
                .flatMap(updatedDoc -> auditService.logAction(username, UPDATE_DOCUMENT_METADATA, updatedDoc.getType(), updatedDoc.getId(),
                        List.of(new MapEntry("updatedKeys", request.metadataToUpdate().keySet()))).thenReturn(updatedDoc)));
    }


    @Override
    @Transactional
    public Mono<Void> deleteDocumentMetadata(UUID documentId, DeleteMetadataRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username ->
                documentRepository.findById(documentId)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                        .flatMap(document -> {
                            Map<String, Object> currentMetadata = jsonUtils.toMap(document.getMetadata());
                            if (currentMetadata == null || currentMetadata.isEmpty() || request.metadataKeysToDelete().isEmpty()) {
                                return Mono.empty(); // No metadata to delete or nothing to do
                            }


                            request.metadataKeysToDelete().forEach(currentMetadata::remove);

                            document.setMetadata(jsonUtils.toJson(currentMetadata));
                            document.setUpdatedAt(OffsetDateTime.now());
                            document.setUpdatedBy(username);
                            return documentRepository.save(document);
                        })
                        .flatMap(updatedDoc -> auditService.logAction(username, DELETE_DOCUMENT_METADATA, updatedDoc.getType(), updatedDoc.getId(),
                                List.of(new MapEntry("deletedKeys", request.metadataKeysToDelete()))))
        );
    }


    @Override
    public Mono<Resource> downloadMultipleDocumentsAsZip(List<UUID> documentIds, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username -> {
            if (documentIds == null || documentIds.isEmpty()) {
                return Mono.error(new IllegalArgumentException("Document IDs list cannot be empty."));
            }

            try {
                PipedInputStream pipedInputStream = new PipedInputStream();
                PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream);
                ZipArchiveOutputStream zos = new ZipArchiveOutputStream(pipedOutputStream);

                documentRepository.findByIdIn(documentIds)
                    .filter(doc -> doc.getType() == FILE)
                    .collectList()
                    .flatMap(docs -> {
                        if (docs.isEmpty()) {
                            return Mono.error(new DocumentNotFoundException("No valid files found for the provided IDs to zip."));
                        }
                        if (docs.size() != documentIds.size()) {
                            log.warn("Some requested documents were not files or not found. Zipping available files.");
                        }
                        return Flux.fromIterable(docs)
                            .concatMap(doc -> addToZip(doc, zos))
                            .then();
                    })
                    .doOnTerminate(() -> closeOutputStream(zos))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                        null,
                        error -> log.error("Error during zip creation", error)
                    );

                return Mono.just(new InputStreamResource(pipedInputStream));
            } catch (IOException e) {
                return Mono.error(new StorageException("Failed to create piped stream", e));
            }
        });
    }

    private static void closeOutputStream(ZipArchiveOutputStream zos) {
        try {
            zos.close();
        } catch (IOException e) {
            log.error("Failed to close zip stream", e);
        }
    }

    private Mono<Void> addToZip(Document doc, ZipArchiveOutputStream zos) {
        return storageService.loadFile(doc.getStoragePath())
                .flatMap(resource -> {
                    if (resource == null || !resource.exists()) {
                        log.warn("Skipping missing file in zip: {}", doc.getName());
                        return Mono.empty();
                    }
                    try {
                        ZipArchiveEntry zipEntry = new ZipArchiveEntry(doc.getName());
                        zipEntry.setSize(doc.getSize() != null ? doc.getSize() : resource.contentLength());
                        zos.putArchiveEntry(zipEntry);
                        try (InputStream is = resource.getInputStream()) {
                            is.transferTo(zos);
                        }
                        zos.closeArchiveEntry();
                        return Mono.empty();
                    } catch (IOException ioe) {
                        return Mono.error(new StorageException(ioe.getMessage()));
                    }
                });
    }


    @Override
    public Flux<UUID> searchDocumentIdsByMetadata(SearchByMetadataRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMapMany(_ -> documentDAO.listDocumentIds(request));
    }

    @Override
    public Mono<Map<String, Object>> getDocumentMetadata(UUID documentId, SearchMetadataRequest request, Authentication auth) {
        return UserPrincipalExtractor.getConnectedUser(auth).flatMap(username ->
                documentRepository.findById(documentId)
                        .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                        .map(document -> {
                            JsonNode metadataNode = jsonUtils.toJsonNode(document.getMetadata());
                            if (metadataNode == null || metadataNode.isNull()) {
                                return new HashMap<String, Object>();
                            }
                            Map<String, Object> allMetadata = objectMapper.convertValue(metadataNode, Map.class);

                            if (request.metadataKeys() != null && !request.metadataKeys().isEmpty()) {
                                Map<String, Object> filteredMetadata = new HashMap<>();
                                for (String key : request.metadataKeys()) {
                                    if (allMetadata.containsKey(key)) {
                                        filteredMetadata.put(key, allMetadata.get(key));
                                    }
                                }
                                return filteredMetadata;
                            }
                            return allMetadata;
                        }));
    }


    // Utility method to find a document
    @Override
    public Mono<Document> findDocumentById(UUID documentId) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)));
    }

    @Override
    public Mono<DocumentInfo> getDocumentInfo(UUID documentId, Boolean withMetadata, Authentication authentication) {
        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException(documentId)))
                .flatMap(doc -> {
            DocumentInfo info = withMetadata != null && withMetadata.booleanValue() ?
                    new DocumentInfo(doc.getType(), doc.getName(), doc.getParentId(), doc.getMetadata() != null ? jsonUtils.toMap(doc.getMetadata()) : null, doc.getSize())
                    : new DocumentInfo(doc.getType(), doc.getName(), doc.getParentId(), null, null);
            return Mono.just(info);
        } );
    }

    @Override
    public Flux<FolderElementInfo> listFolderInfo(UUID folderId, Boolean onlyFiles, Boolean onlyFolders, Authentication authentication) {
        if(onlyFiles != null && onlyFiles && onlyFolders != null && onlyFolders) {
            return Flux.error(new IllegalArgumentException("onlyFiles and onlyFolders cannot be true in simultaneously"));
        }
        if(folderId == null) {
            if(onlyFiles != null && onlyFiles) {
                return documentRepository.listDocumentInfoAtRootLevel(FILE);
            } else  if(onlyFolders != null && onlyFolders) {
                return documentRepository.listDocumentInfoAtRootLevel(FOLDER);
            }
            return documentRepository.listDocumentInfoAtRootLevel();
        }
        return documentRepository.existsByIdAndType(folderId, DocumentType.FOLDER)
                .flatMapMany(exists -> {
                   if(!exists) {
                       return Flux.error(new DocumentNotFoundException(FOLDER, folderId));
                   }
                    return documentRepository.listDocumentInfoInFolder(folderId);
                });
    }

}