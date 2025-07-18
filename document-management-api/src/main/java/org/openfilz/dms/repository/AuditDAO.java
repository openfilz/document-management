package org.openfilz.dms.repository;

import org.openfilz.dms.dto.AuditLog;
import org.openfilz.dms.dto.SearchByAuditLogRequest;
import org.openfilz.dms.dto.SortOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public interface AuditDAO {
    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, Map<String, Object> details);
    Flux<AuditLog> getAuditTrail(UUID resourceId, SortOrder sort);
    Flux<AuditLog> searchAuditTrail(SearchByAuditLogRequest request);
}
