package org.openfilz.dms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.scalars.ExtendedScalars;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.mapper.DocumentMapper;
import org.openfilz.dms.repository.impl.DocumentDataFetcherImpl;
import org.openfilz.dms.utils.SqlUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.r2dbc.core.DatabaseClient;

import static org.openfilz.dms.config.GraphQlQueryConfig.LIST_FOLDER;
import static org.openfilz.dms.config.GraphQlQueryConfig.QUERY;

@RequiredArgsConstructor
@Configuration
public class GraphQlConfig {

    private final DatabaseClient databaseClient;

    private final DocumentMapper mapper;

    private final ObjectMapper  objectMapper;

    private final SqlUtils sqlUtils;

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(ExtendedScalars.Json)
                .scalar(ExtendedScalars.UUID)
                .scalar(ExtendedScalars.GraphQLLong)
                .scalar(ExtendedScalars.DateTime)
                .type(QUERY, builder -> builder.dataFetcher(LIST_FOLDER, new DocumentDataFetcherImpl(databaseClient, mapper, objectMapper, sqlUtils)));
    }


}