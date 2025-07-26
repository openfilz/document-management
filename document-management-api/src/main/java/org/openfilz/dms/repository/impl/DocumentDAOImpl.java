package org.openfilz.dms.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfilz.dms.dto.request.SearchByMetadataRequest;
import org.openfilz.dms.repository.DocumentDAO;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.openfilz.dms.utils.SqlUtils.isFirst;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDAOImpl implements DocumentDAO {

    private final DatabaseClient databaseClient;

    private  final ObjectMapper objectMapper;

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
        StringBuilder sql = new StringBuilder("SELECT id FROM documents WHERE ");
        boolean first = true;
        if(metadataCriteria) {
            sql.append("metadata @> :criteria::jsonb ");
            first = false;
        }
        if(nameCriteria) {
            first = isFirst(first, sql);
            sql.append("name = :name ");
        }
        if(typeCriteria) {
            first = isFirst(first, sql);
            sql.append("type = :type ");
        }
        if(parentFolderCriteria) {
            first = isFirst(first, sql);
            sql.append("parent_id = :parent_id ");
        }
        if(rootOnlyCriteria) {
            isFirst(first, sql);
            sql.append("parent_id is null");
        }
        DatabaseClient.GenericExecuteSpec query = databaseClient.sql(sql.toString());
        if(metadataCriteria) {
            try {
                String criteriaJson = objectMapper.writeValueAsString(request.metadataCriteria());
                query = query.bind("criteria", criteriaJson);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if(nameCriteria) {
            query = query.bind("name", request.name());
        }
        if(typeCriteria) {
            query = query.bind("type", request.type().toString());
        }
        if(parentFolderCriteria) {
            query = query.bind("parent_id", request.parentFolderId());
        }
        return query.map( row -> row.get("id", UUID.class)).all();
    }


}
