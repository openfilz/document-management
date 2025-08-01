package org.openfilz.dms.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.dto.request.ListFolderRequest;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import org.openfilz.dms.entity.Document;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.mapper.DocumentMapper;
import org.openfilz.dms.repository.DocumentDataFetcher;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.data.util.ParsingUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.openfilz.dms.entity.DocumentSqlMapping.*;
import static org.openfilz.dms.utils.SqlUtils.SPACE;
import static org.openfilz.dms.utils.SqlUtils.isFirst;

@RequiredArgsConstructor
public class DocumentDataFetcherImpl implements DocumentDataFetcher {

    private static final int MAX_PAGE_SIZE = 100;

    private static final String UNDERSCORE = "_";

    private static final Map<String, String> DOCUMENT_FIELD_SQL_MAP;
    public static final String FROM_DOCUMENTS = " from Documents";

    public static final String OFFSET = " OFFSET ";
    public static final String LIMIT = " LIMIT ";

    public static final String SELECT = "select ";
    public static final String COMMA = ", ";
    public static final String GRAPHQL_REQUEST = "request";

    static {
        try {
            DOCUMENT_FIELD_SQL_MAP = Arrays.stream(Introspector.getBeanInfo(Document.class)
                            .getPropertyDescriptors())
                    .collect(Collectors.toMap(FeatureDescriptor::getName,
                            pd -> ParsingUtils.reconcatenateCamelCase(pd.getName(), UNDERSCORE)));
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    private final DatabaseClient databaseClient;
    private final DocumentMapper mapper;
    private final ObjectMapper  objectMapper;
    private final SqlUtils sqlUtils;

    @Override
    public Flux<FullDocumentInfo> get(DataFetchingEnvironment environment) throws Exception {
        List<SelectedField> objectFields = environment.getSelectionSet().getFields();
        List<String> sqlFields = getSqlFields(objectFields);
        ListFolderRequest filter = null;
        StringBuilder query = toSelect(sqlFields).append(FROM_DOCUMENTS);
        DatabaseClient.GenericExecuteSpec sqlQuery = null;
        if(environment.getArguments() != null) {
            Object request = environment.getArguments().get(GRAPHQL_REQUEST);
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
        if(request.pageInfo().pageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageInfo.pageSize must be less than " + MAX_PAGE_SIZE);
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
        query.append(" ORDER BY ").append(DOCUMENT_FIELD_SQL_MAP.get(request.pageInfo().sortBy()));
        if(request.pageInfo().sortOrder() != null) {
            query.append(SPACE).append(request.pageInfo().sortOrder());
        }
    }

    private void appendOffsetLimit(StringBuilder query, ListFolderRequest request) {
        query.append(LIMIT).append(request.pageInfo().pageSize())
                .append(OFFSET).append((request.pageInfo().pageNumber() - 1) * request.pageInfo().pageSize());
    }


    private Document buildDocument(Readable row, List<String> fields) {
        Document.DocumentBuilder builder = Document.builder();
        fields.forEach(field -> {
            switch (field) {
                case ID -> builder.id(row.get(field, UUID.class));
                case NAME -> builder.name(row.get(field, String.class));
                case TYPE -> builder.type(DocumentType.valueOf(row.get(field, String.class)));
                case SIZE -> builder.size(row.get(field, Long.class));
                case METADATA -> builder.metadata(row.get(field, Json.class));
                case CREATED_AT -> builder.createdAt(row.get(field, OffsetDateTime.class));
                case UPDATED_AT -> builder.updatedAt(row.get(field, OffsetDateTime.class));
                case CREATED_BY -> builder.createdBy(row.get(field, String.class));
                case UPDATED_BY -> builder.updatedBy(row.get(field, String.class));
                case CONTENT_TYPE -> builder.contentType(row.get(field, String.class));
            }
        });
        return builder.build();
    }

    private List<String> getSqlFields(List<SelectedField> fields) {
        return fields.stream()
                .map(field -> DOCUMENT_FIELD_SQL_MAP.get(field.getName()))
                .toList();
    }

    private StringBuilder toSelect(List<String> fields) {
        return new StringBuilder(SELECT).append(String.join(COMMA, fields));
    }
}
