// com/example/dms/service/AuditService.java
package org.openfilz.dms.service;

import org.openfilz.dms.dto.AuditLog;
import org.openfilz.dms.dto.SearchByAuditLogRequest;
import org.openfilz.dms.dto.SortOrder;
import org.openfilz.dms.utils.MapEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, List<MapEntry> details);

    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, Record record);

    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId);

    Flux<AuditLog> getAuditTrail(UUID resourceId, SortOrder sort);

    Flux<AuditLog> searchAuditTrail(SearchByAuditLogRequest request);
}