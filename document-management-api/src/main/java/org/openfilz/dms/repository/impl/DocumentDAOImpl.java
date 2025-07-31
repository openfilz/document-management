package org.openfilz.dms.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfilz.dms.dto.request.SearchByMetadataRequest;
import org.openfilz.dms.repository.DocumentDAO;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.openfilz.dms.entity.DocumentSqlMapping.*;
import static org.openfilz.dms.utils.SqlUtils.isFirst;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDAOImpl implements DocumentDAO {

    public static final String SELECT_ID_FROM_DOCUMENTS = "SELECT id FROM documents";
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
            sqlUtils.appendEqualsCriteria(PARENT_ID, sql);
        }
        if(rootOnlyCriteria) {
            isFirst(first, sql);
            sqlUtils.appendIsNullCriteria(PARENT_ID, sql);
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
            query = sqlUtils.bindCriteria(PARENT_ID, request.parentFolderId(), query);
        }
        return query.map( row -> row.get(ID, UUID.class)).all();
    }




}
