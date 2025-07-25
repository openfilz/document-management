// com/example/dms/service/impl/AuditServiceImpl.java
package org.openfilz.dms.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfilz.dms.dto.audit.AuditLog;
import org.openfilz.dms.dto.audit.AuditLogDetails;
import org.openfilz.dms.dto.request.SearchByAuditLogRequest;
import org.openfilz.dms.enums.SortOrder;
import org.openfilz.dms.enums.AuditAction;
import org.openfilz.dms.enums.DocumentType;
import org.openfilz.dms.exception.AuditException;
import org.openfilz.dms.repository.AuditDAO;
import org.openfilz.dms.service.AuditService;
import org.openfilz.dms.utils.MapEntry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditDAO auditDAO;

    @Override
    public Mono<Void> logAction(String userPrincipal, AuditAction action, DocumentType resourceType, UUID resourceId, AuditLogDetails details) {
        return auditDAO.logAction(userPrincipal, action, resourceType, resourceId, details);
    }

    @Override
    public Mono<Void> logAction(String userPrincipal, AuditAction action, DocumentType resourceType, UUID resourceId) {
        return auditDAO.logAction(userPrincipal, action, resourceType, resourceId, null);
    }

    @Override
    public Flux<AuditLog> getAuditTrail(UUID resourceId, SortOrder sort) {
        return auditDAO.getAuditTrail(resourceId, sort == null ? SortOrder.DESC : sort);
    }

    @Override
    public Flux<AuditLog> searchAuditTrail(SearchByAuditLogRequest request) {
        return auditDAO.searchAuditTrail(request);
    }


    private static Map<String, Object> recordToMap(Record record) {
        return toMap(Stream.of(record.getClass().getRecordComponents())
                .map(component -> {
                    try {
                        return new MapEntry(component.getName(), component.getAccessor().invoke(record));
                    } catch (Exception e) {
                        throw new AuditException("Error accessing record component", e);
                    }
                }));
    }

    private static Map<String, Object> toMap(Stream<MapEntry> stream) {
        return stream
                .filter(entry-> entry.getValue() != null)
                .collect(Collectors.toMap(MapEntry::getKey, MapEntry::getValue));
    }

}