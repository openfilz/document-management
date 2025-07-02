// com/example/dms/repository/DocumentRepository.java
package org.openfilz.dms.repository;

import org.openfilz.dms.dto.FolderElementInfo;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.DocumentType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends ReactiveCrudRepository<Document, UUID> {

    //Flux<Document> findByParentId(UUID parentId);

    Mono<Boolean> existsByNameAndParentId(String name, UUID parentId);

    Mono<Boolean> existsByNameAndParentIdIsNull(String name);

    Mono<Boolean> existsByIdAndType(UUID id, DocumentType type);

    @Query("SELECT * FROM documents WHERE metadata @> :criteria::jsonb")
        // @> checks if left JSON contains right JSON
    Flux<Document> findByMetadata(@Param("criteria") String criteriaJson); // Pass criteria as JSON string

    @Query("SELECT id, type, name FROM documents WHERE parent_id = :id")
    Flux<FolderElementInfo> listDocumentInfoInFolder(UUID id);

    @Query("SELECT id, type, name FROM documents WHERE parent_id is null")
    Flux<FolderElementInfo> listDocumentInfoAtRootLevel();

    @Query("SELECT id, type, name FROM documents WHERE parent_id is null and type = :type")
    Flux<FolderElementInfo> listDocumentInfoAtRootLevel(DocumentType type);

    @Query("SELECT id FROM documents WHERE parent_id is null and type = :type")
    Flux<UUID> listDocumentIdsAtRootLevel(DocumentType type);

    @Query("SELECT id FROM documents WHERE parent_id is null and type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsAtRootLevelWithMetadata(DocumentType type, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE type = :type")
    Flux<UUID> listDocumentIds(DocumentType type);

    @Query("SELECT id FROM documents WHERE type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithMetadata(DocumentType type, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE parent_id is null")
    Flux<UUID> listDocumentIdsAtRootLevel();

    @Query("SELECT id FROM documents WHERE parent_id is null and name = :name")
    Flux<UUID> listDocumentIdsWithNameAtRootLevel(String name);

    @Query("SELECT id FROM documents WHERE parent_id is null and name = :name and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameAtRootLevelWithMetadata(String name, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE parent_id is null and name = :name and type = :type")
    Flux<UUID> listDocumentIdsWithNameAndTypeAtRootLevel(String name, DocumentType type);

    @Query("SELECT id FROM documents WHERE parent_id is null and name = :name and type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameAndTypeAndMetadataAtRootLevel(String name, DocumentType type, @Param("criteria") String criteriaJson);


    @Query("SELECT id FROM documents WHERE name = :name")
    Flux<UUID> listDocumentIdsWithName(String name);

    @Query("SELECT id FROM documents WHERE name = :name and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameWithMetadata(String name, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE name = :name and type = :type")
    Flux<UUID> listDocumentIdsWithNameAndType(String name, DocumentType type);

    @Query("SELECT id FROM documents WHERE name = :name and type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameAndTypeAndMetadata(String name, DocumentType type, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId")
    Flux<UUID> listDocumentIds(UUID parentId);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and type = :type")
    Flux<UUID> listDocumentIdsWithType(UUID parentId, DocumentType type);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithTypeWithMetadata(UUID parentId, DocumentType type, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and name = :name")
    Flux<UUID> listDocumentIdsWithName(UUID parentId, String name);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and name = :name and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameWithMetadata(UUID parentId, String name, @Param("criteria") String criteriaJson);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and name = :name and type = :type")
    Flux<UUID> listDocumentIdsWithNameAndType(UUID parentId, String name, DocumentType type);

    @Query("SELECT id FROM documents WHERE parent_id = :parentId and name = :name and type = :type and metadata @> :criteria::jsonb")
    Flux<UUID> listDocumentIdsWithNameAndTypeAndMetadata(UUID parentId, String name, DocumentType type, @Param("criteria") String criteriaJson);


    /*@Query("UPDATE documents SET parent_id = :newParentId, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE id = :id")
    Mono<Void> updateParentId(UUID id, UUID newParentId, String updatedBy);

    @Query("UPDATE documents SET name = :newName, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE id = :id")
    Mono<Void> updateName(UUID id, String newName, String updatedBy);

    @Query("UPDATE documents SET metadata = metadata || :metadataToUpdate::jsonb, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE id = :id")
    Mono<Void> updateMetadata(@Param("id") UUID id, @Param("metadataToUpdate") String metadataJson, String updatedBy);

    @Query("UPDATE documents SET metadata = :newMetadata::jsonb, updated_at = CURRENT_TIMESTAMP, updated_by = :updatedBy WHERE id = :id")
    Mono<Void> replaceMetadata(@Param("id") UUID id, @Param("newMetadata") String metadataJson, String updatedBy);

    @Query("UPDATE documents SET metadata = metadata - :keyToRemove WHERE id = :id AND metadata ? :keyToRemove")
    Mono<Void> deleteMetadataByKey(@Param("id") UUID id, @Param("keyToRemove") String keyToRemove, String updatedBy);*/

    Flux<Document> findByIdIn(List<UUID> ids);

    Flux<Document> findByParentIdAndType(UUID parentId, DocumentType type);

    // For recursive fetching of folder contents (can be complex with R2DBC, might need multiple queries or a DB function)
    // Simplified: get immediate children
    //Flux<Document> findAllByParentId(UUID folderId);
}