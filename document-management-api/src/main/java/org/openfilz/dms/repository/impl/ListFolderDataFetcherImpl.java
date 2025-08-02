package org.openfilz.dms.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import org.openfilz.dms.config.GraphQlQueryConfig;
import org.openfilz.dms.dto.request.ListFolderRequest;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import org.openfilz.dms.mapper.DocumentMapper;
import org.openfilz.dms.repository.ListFolderDataFetcher;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.openfilz.dms.entity.DocumentSqlMapping.*;
import static org.openfilz.dms.utils.SqlUtils.SPACE;
import static org.openfilz.dms.utils.SqlUtils.isFirst;

//@Slf4j
public class ListFolderDataFetcherImpl extends AbstractDataFetcher implements ListFolderDataFetcher {

    public ListFolderDataFetcherImpl(DatabaseClient databaseClient, DocumentMapper mapper, ObjectMapper objectMapper, SqlUtils sqlUtils) {
        super(databaseClient, mapper, objectMapper, sqlUtils);
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
                checkFilter(filter);
                applyFilter(query, filter);
                sqlQuery = bindCriteria(databaseClient.sql(query.toString()), filter);
            }
        }
        if(sqlQuery == null) {
            sqlQuery = databaseClient.sql(query.toString());
        }
        //log.debug("GraphQL - SQL query : {}", query);
        return sqlQuery.map(row -> mapper.toFullDocumentInfo(buildDocument(row, sqlFields)))
                .all();
    }

    private DatabaseClient.GenericExecuteSpec bindCriteria(DatabaseClient.GenericExecuteSpec query, ListFolderRequest filter) {
        if(filter.id() != null) {
            query = sqlUtils.bindCriteria(PARENT_ID, filter.id(), query);
        }
        if(filter.name() != null) {
            query = sqlUtils.bindCriteria(NAME, filter.name(), query);
        }
        if(filter.nameLike() != null) {
            query = sqlUtils.bindLikeCriteria(NAME, filter.nameLike(), query);
        }
        if(filter.type() != null) {
            query = sqlUtils.bindCriteria(TYPE, filter.type().toString(), query);
        }
        if(filter.contentType() != null) {
            query = sqlUtils.bindCriteria(CONTENT_TYPE, filter.contentType(), query);
        }
        if(filter.metadata() != null) {
            query = sqlUtils.bindMetadata(filter.metadata(), query);
        }
        if(filter.size() != null) {
            query = sqlUtils.bindCriteria(SIZE, filter.size(), query);
        }
        if(filter.createdAtBefore() != null) {
            if(filter.createdAtAfter() != null) {
                query = sqlUtils.bindCriteria(CREATED_AT_FROM, SqlUtils.stringToDate(filter.createdAtAfter()), query);
                query = sqlUtils.bindCriteria(CREATED_AT_TO, SqlUtils.stringToDate(filter.createdAtBefore()), query);
            } else {
                query = sqlUtils.bindCriteria(CREATED_AT, SqlUtils.stringToDate(filter.createdAtBefore()), query);
            }
        } else if(filter.createdAtAfter() != null) {
            query = sqlUtils.bindCriteria(CREATED_AT, SqlUtils.stringToDate(filter.createdAtAfter()), query);
        }
        if(filter.updatedAtBefore() != null) {
            if(filter.updatedAtAfter() != null) {
                query = sqlUtils.bindCriteria(UPDATED_AT_FROM, SqlUtils.stringToDate(filter.updatedAtAfter()), query);
                query = sqlUtils.bindCriteria(UPDATED_AT_TO, SqlUtils.stringToDate(filter.updatedAtBefore()), query);
            } else {
                query = sqlUtils.bindCriteria(UPDATED_AT, SqlUtils.stringToDate(filter.updatedAtBefore()), query);
            }
        } else if(filter.updatedAtAfter() != null) {
            query = sqlUtils.bindCriteria(UPDATED_AT, SqlUtils.stringToDate(filter.updatedAtAfter()), query);
        }
        if(filter.createdBy() != null) {
            query = sqlUtils.bindCriteria(CREATED_BY, filter.createdBy(), query);
        }
        if(filter.updatedBy() != null) {
            query = sqlUtils.bindCriteria(UPDATED_BY, filter.updatedBy(), query);
        }
        return query;
    }

    private void checkFilter(ListFolderRequest filter) {
        if(filter.name() != null && filter.nameLike() != null) {
            throw new IllegalArgumentException("name and nameLike cannot be used simultaneously : choose name or nameLike in your filter");
        }
    }

    private void applyFilter(StringBuilder query, ListFolderRequest request) {
        if(request.pageInfo() == null) {
            throw new IllegalArgumentException("page info is required");
        }
        if(request.pageInfo().pageNumber() == null || request.pageInfo().pageNumber() < 1 ) {
            throw new IllegalArgumentException("pageInfo.pageNumber must be greater than 1");
        }
        if(request.pageInfo().pageSize() > SqlUtils.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageInfo.pageSize must be less than " + SqlUtils.MAX_PAGE_SIZE);
        }
        boolean first = true;
        if(request.id() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(PARENT_ID, query);
        } else {
            first = isFirst(first, query);
            sqlUtils.appendIsNullCriteria(PARENT_ID, query);
        }
        if(request.type() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(TYPE, query);
        }
        if(request.contentType() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(CONTENT_TYPE, query);
        }
        if(request.name() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(NAME, query);
        }
        if(request.nameLike() != null) {
            first = isFirst(first, query);
            sqlUtils.appendLikeCriteria(NAME, query);
        }
        if(request.metadata() != null && !request.metadata().isEmpty()) {
            first = isFirst(first, query);
            sqlUtils.appendJsonEqualsCriteria(METADATA, query);
        }
        if(request.size() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(SIZE, query);
        }
        if(request.createdBy() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(CREATED_BY, query);
        }
        if(request.updatedBy() != null) {
            first = isFirst(first, query);
            sqlUtils.appendEqualsCriteria(UPDATED_BY, query);
        }
        if(request.createdAtBefore() != null) {
            first = isFirst(first, query);
            if(request.createdAtAfter() != null) {
                sqlUtils.appendBetweenCriteria(CREATED_AT, query);
            } else {
                sqlUtils.appendLessThanCriteria(CREATED_AT, query);
            }
        } else if(request.createdAtAfter() != null) {
            first = isFirst(first, query);
            sqlUtils.appendGreaterThanCriteria(CREATED_AT, query);
        }
        if(request.updatedAtBefore() != null) {
            first = isFirst(first, query);
            if(request.updatedAtAfter() != null) {
                sqlUtils.appendBetweenCriteria(UPDATED_AT, query);
            } else {
                sqlUtils.appendLessThanCriteria(UPDATED_AT, query);
            }
        } else if(request.updatedAtAfter() != null) {
            first = isFirst(first, query);
            sqlUtils.appendGreaterThanCriteria(UPDATED_AT, query);
        }
        if(request.pageInfo().sortBy() != null) {
            appendSort(query, request);
        }
        appendOffsetLimit(query, request);
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
