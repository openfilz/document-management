package org.openfilz.dms.repository;

import org.openfilz.dms.dto.request.SearchByMetadataRequest;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface DocumentDAO {
    Flux<UUID> listDocumentIds(SearchByMetadataRequest request);
}
