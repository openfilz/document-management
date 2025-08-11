package org.openfilz.dms.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.openfilz.dms.config.GraphQlQueryConfig;
import org.openfilz.dms.dto.request.ListFolderRequest;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import org.openfilz.dms.mapper.DocumentMapper;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.openfilz.dms.utils.SqlUtils.SPACE;

//@Slf4j
public class ListFolderDataFetcherImpl extends AbstractDataFetcher<Flux<FullDocumentInfo>, FullDocumentInfo> {


    private final ListFolderCriteria criteria;

    public ListFolderDataFetcherImpl(DatabaseClient databaseClient, DocumentMapper mapper, ObjectMapper objectMapper, SqlUtils sqlUtils, ListFolderCriteria listFolderCriteria) {
        super(databaseClient, mapper, objectMapper, sqlUtils);
        this.criteria = listFolderCriteria;
    }


    @Override
    public Flux<FullDocumentInfo> get(DataFetchingEnvironment environment) throws Exception {
        List<String> sqlFields = getSqlFields(environment);
        StringBuilder query = toSelect(sqlFields).append(SqlUtils.FROM_DOCUMENTS);
        ListFolderRequest filter = null;
        DatabaseClient.GenericExecuteSpec sqlQuery = null;
        if(environment.getArguments() != null) {
            Object request = environment.getArguments().get(GraphQlQueryConfig.GRAPHQL_REQUEST);
            if(request != null) {
                filter = objectMapper.convertValue(request, ListFolderRequest.class);
                if(filter.pageInfo() == null || filter.pageInfo().pageSize() == null || filter.pageInfo().pageNumber() == null) {
                    throw new IllegalArgumentException("Paging information must be provided");
                }
                criteria.checkFilter(filter);
                criteria.checkPageInfo(filter);
                criteria.applyFilter(query, filter);
                applySort(query, filter);
                appendOffsetLimit(query, filter);
                sqlQuery = criteria.bindCriteria(databaseClient.sql(query.toString()), filter);
            }
        }
        if(sqlQuery == null) {
            throw new IllegalArgumentException("At least paging information is required");
        }
        //log.debug("GraphQL - SQL query : {}", query);
        return sqlQuery.map(row -> mapResultRow(row, sqlFields))
                .all();
    }

    private void applySort(StringBuilder query, ListFolderRequest request) {
        if(request.pageInfo().sortBy() != null) {
            appendSort(query, request);
        }
    }

    @Override
    protected FullDocumentInfo mapResultRow(io.r2dbc.spi.Readable row, List<String> sqlFields) {
        return mapper.toFullDocumentInfo(buildDocument(row, sqlFields));
    }



    private void appendSort(StringBuilder query, ListFolderRequest request) {
        query.append(SqlUtils.ORDER_BY).append(DOCUMENT_FIELD_SQL_MAP.get(request.pageInfo().sortBy()));
        if(request.pageInfo().sortOrder() != null) {
            query.append(SPACE).append(request.pageInfo().sortOrder());
        }
    }

    private void appendOffsetLimit(StringBuilder query, ListFolderRequest request) {
        query.append(SqlUtils.LIMIT).append(request.pageInfo().pageSize())
                .append(SqlUtils.OFFSET).append((request.pageInfo().pageNumber() - 1) * request.pageInfo().pageSize());
    }

}
