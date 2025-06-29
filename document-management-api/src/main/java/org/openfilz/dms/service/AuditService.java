// com/example/dms/service/AuditService.java
package org.openfilz.dms.service;

import org.openfilz.dms.utils.MapEntry;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, List<MapEntry> details);

    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, Record record);

    Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId);
}