package org.openfilz.dms.repository;

import graphql.schema.DataFetcher;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import reactor.core.publisher.Mono;

public interface DocumentDataFetcher extends DataFetcher<Mono<FullDocumentInfo>> {
}
