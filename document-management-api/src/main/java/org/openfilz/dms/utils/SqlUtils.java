package org.openfilz.dms.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class SqlUtils {

    private static final String AND = "AND ";
    private static final String WHERE = " WHERE ";

    private final ObjectMapper objectMapper;

    public static boolean isFirst(boolean first, StringBuilder sql) {
        if(!first) {
            sql.append(AND);
        } else {
            sql.append(WHERE);
            first = false;
        }
        return first;
    }

    public DatabaseClient.GenericExecuteSpec bindCriteria(String criteria, Object value, DatabaseClient.GenericExecuteSpec query) {
        return query.bind(criteria, value);
    }

    public DatabaseClient.GenericExecuteSpec bindMetadata(Map<String, Object> metadata, DatabaseClient.GenericExecuteSpec query) {
        try {
            String criteriaJson = objectMapper.writeValueAsString(metadata);
            return query.bind("criteria", criteriaJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void appendEqualsCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" = :").append(criteria).append(" ");
    }

    public void appendLessThanCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" <= :").append(criteria).append(" ");
    }

    public void appendGreaterThanCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" >= :").append(criteria).append(" ");
    }

    public void appendBetweenCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" between :").append(criteria).append("_from and :").append(criteria).append("_to");
    }

    public void appendIsNullCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" is null ");
    }

    public void appendJsonEqualsCriteria(String criteria, StringBuilder sql) {
        sql.append(criteria).append(" @> :criteria::jsonb ");
    }


}
