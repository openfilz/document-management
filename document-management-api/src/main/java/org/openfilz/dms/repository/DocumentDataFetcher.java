package org.openfilz.dms.repository;

import graphql.schema.DataFetcher;
import org.openfilz.dms.dto.response.FullDocumentInfo;
import reactor.core.publisher.Flux;

public interface DocumentDataFetcher extends DataFetcher<Flux<FullDocumentInfo>> {
}
