package org.openfilz.dms.repository.impl;

import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfilz.dms.dto.request.SearchByMetadataRequest;
import org.openfilz.dms.dto.response.ChildElementInfo;
import org.openfilz.dms.entity.DocumentSqlMapping;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.repository.DocumentDAO;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.List;
import java.util.UUID;

import static org.openfilz.dms.entity.DocumentSqlMapping.*;
import static org.openfilz.dms.utils.FileConstants.SLASH;
import static org.openfilz.dms.utils.SqlUtils.isFirst;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDAOImpl implements DocumentDAO {

    public static final String SELECT_ID_FROM_DOCUMENTS = "SELECT id FROM documents";

    public static final String SELECT_CHILDREN = """
            WITH RECURSIVE folder_tree AS (
              SELECT
                 id,
                 name,
                 type,
                 size,
                 storage_path as storage,
                 name::text as fullpath
              FROM documents
              WHERE parent_id = :parentId
             UNION ALL
              SELECT
                 d.id,
                 d.name,
                 d.type,
                 d.size,
                 storage_path,
                 tree.fullpath || '/' || d.name
              FROM documents d
              JOIN folder_tree tree ON d.parent_id = tree.id
             )
             SELECT * FROM folder_tree""";


    public static final String SELECT_CHILDREN_2 = """
            WITH RECURSIVE folder_tree AS (
              SELECT
                 id,
                 name,
                 type,
                 size,
                 storage_path as storage,
                 :rootFolder || name as fullpath
              FROM documents
              WHERE parent_id = :parentId
             UNION ALL
              SELECT
                 d.id,
                 d.name,
                 d.type,
                 d.size,
                 storage_path,
                 tree.fullpath || '/' || d.name
              FROM documents d
              JOIN folder_tree tree ON d.parent_id = tree.id
             )
             SELECT * FROM folder_tree""";



    public static final String FULLPATH = "fullpath";
    public static final String STORAGE = "storage";
    public static final String PARENT_ID = "parentId";
    public static final String IDS = "ids";


    private final DatabaseClient databaseClient;

    private  final SqlUtils sqlUtils;

    @Override
    public Flux<UUID> listDocumentIds(SearchByMetadataRequest request) {
        boolean metadataCriteria = request.metadataCriteria() != null && !request.metadataCriteria().isEmpty();
        boolean nameCriteria = request.name() != null && !request.name().isEmpty();
        boolean typeCriteria = request.type() != null;
        boolean parentFolderCriteria = request.parentFolderId() != null;
        boolean rootOnlyCriteria = request.rootOnly() != null;
        if(parentFolderCriteria && (rootOnlyCriteria && request.rootOnly())) {
            return Flux.error(new IllegalArgumentException("Impossible to specify simultaneously rootOnly 'true' and parentFolderId not null"));
        }
        if(!nameCriteria
                && !typeCriteria
                && !metadataCriteria) {
            return Flux.error(new IllegalArgumentException("All criteria cannot be empty."));
        }
        StringBuilder sql = new StringBuilder(SELECT_ID_FROM_DOCUMENTS);
        boolean first = true;
        if(metadataCriteria) {
            first = isFirst(first, sql);
            sqlUtils.appendJsonEqualsCriteria(METADATA, sql);
        }
        if(nameCriteria) {
            first = isFirst(first, sql);
            sqlUtils.appendEqualsCriteria(NAME, sql);
        }
        if(typeCriteria) {
            first = isFirst(first, sql);
            sqlUtils.appendEqualsCriteria(TYPE, sql);
        }
        if(parentFolderCriteria) {
            first = isFirst(first, sql);
            sqlUtils.appendEqualsCriteria(DocumentSqlMapping.PARENT_ID, sql);
        }
        if(rootOnlyCriteria) {
            isFirst(first, sql);
            sqlUtils.appendIsNullCriteria(DocumentSqlMapping.PARENT_ID, sql);
        }
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(sql.toString());
        if(metadataCriteria) {
            query = sqlUtils.bindMetadata(request.metadataCriteria(),  query);
        }
        if(nameCriteria) {
            query = sqlUtils.bindCriteria(NAME, request.name(), query);
        }
        if(typeCriteria) {
            query = sqlUtils.bindCriteria(TYPE, request.type().toString(), query);
        }
        if(parentFolderCriteria) {
            query = sqlUtils.bindCriteria(DocumentSqlMapping.PARENT_ID, request.parentFolderId(), query);
        }
        return query.map( row -> row.get(ID, UUID.class)).all();
    }

    public Flux<ChildElementInfo> getChildren(Flux<Tuple2<UUID, String>> folderIds) {
        return folderIds.flatMap(folders -> databaseClient.sql(SELECT_CHILDREN_2)
                .bind("rootFolder", folders.getT2() + SLASH)
                .bind(PARENT_ID, folders.getT1())
                .map(this::toChild)
                .all());
    }

    @Override
    public Flux<ChildElementInfo> getChildren(UUID folderId) {
        return databaseClient.sql(SELECT_CHILDREN).bind(PARENT_ID, folderId).map(this::toChild).all();
    }

    @Override
    public Flux<ChildElementInfo> getElementsAndChildren(List<UUID> documentIds) {
        return databaseClient.sql("""
            SELECT
                id,
                name,
                type,
                size,
                storage_path
            FROM documents
            where id in (:ids)""")
                .bind(IDS, documentIds)
                .map(this::toRootChild)
                .all()
                .mergeWith(getChildren(getFolders(documentIds)));

    }

    private Flux<Tuple2<UUID, String>> getFolders(List<UUID> documentIds) {
        return databaseClient.sql("select id, name from documents where type = :type and id in (:ids)")
                .bind(TYPE, DocumentType.FOLDER.toString())
                .bind(IDS, documentIds)
                .map(row -> Tuples.of(row.get(ID, UUID.class), row.get(NAME, String.class)))
                .all();
    }

    private ChildElementInfo toRootChild(io.r2dbc.spi.Readable row) {
        return getChildElementInfo(row, NAME, STORAGE_PATH);

    }

    private ChildElementInfo toChild(io.r2dbc.spi.Readable row) {
        return getChildElementInfo(row, FULLPATH, STORAGE);
    }

    private ChildElementInfo getChildElementInfo(Readable row, String fullpath, String storage) {
        return ChildElementInfo.builder()
                .id(row.get(ID, UUID.class))
                .name(row.get(NAME, String.class))
                .path(row.get(fullpath, String.class))
                .storagePath(row.get(storage, String.class))
                .type(DocumentType.valueOf(row.get(TYPE, String.class)))
                .size(row.get(SIZE, Long.class))
                .build();
    }


}
