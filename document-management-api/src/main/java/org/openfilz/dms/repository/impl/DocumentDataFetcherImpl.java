package org.openfilz.dms.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import org.openfilz.dms.mapper.DocumentMapper;
import org.openfilz.dms.repository.DocumentDataFetcher;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;


import static org.openfilz.dms.entity.DocumentSqlMapping.ID;
import static org.openfilz.dms.utils.SqlUtils.FROM_DOCUMENTS;
import static org.openfilz.dms.utils.SqlUtils.WHERE;

public class DocumentDataFetcherImpl extends AbstractDataFetcher implements DocumentDataFetcher {

    public DocumentDataFetcherImpl(DatabaseClient databaseClient, DocumentMapper mapper, ObjectMapper objectMapper, SqlUtils sqlUtils) {
        super(databaseClient, mapper, objectMapper, sqlUtils);
    }

    @Override
    public Mono<FullDocumentInfo> get(DataFetchingEnvironment environment) throws Exception {
        List<String> sqlFields = getSqlFields(environment);
        StringBuilder query = toSelect(sqlFields).append(FROM_DOCUMENTS).append(WHERE);
        sqlUtils.appendEqualsCriteria(ID, query);
        UUID uuid = (UUID) environment.getArguments().get(ID);
        DatabaseClient.GenericExecuteSpec sqlQuery = sqlUtils.bindCriteria(ID, uuid, databaseClient.sql(query.toString()));
        return sqlQuery.map(row -> mapper.toFullDocumentInfo(buildDocument(row, sqlFields)))
                .one();
    }
}
