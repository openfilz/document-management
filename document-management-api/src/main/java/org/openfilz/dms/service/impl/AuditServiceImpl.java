// com/example/dms/service/impl/AuditServiceImpl.java
package org.openfilz.dms.service.impl;

import io.r2dbc.postgresql.codec.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfilz.dms.exception.AuditException;
import org.openfilz.dms.service.AuditService;
import org.openfilz.dms.utils.JsonUtils;
import org.openfilz.dms.utils.MapEntry;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final DatabaseClient databaseClient;
    private final JsonUtils jsonUtils;

    @Override
    public Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, List<MapEntry> details) {

        return doLogAction(userPrincipal, action, resourceType, resourceId, toMap(details.stream()));
    }

    @Override
    public Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId, Record record) {
        Map<String, Object> details = recordToMap(record);
        return doLogAction(userPrincipal, action, resourceType, resourceId, details);
    }

    @Override
    public Mono<Void> logAction(String userPrincipal, String action, String resourceType, UUID resourceId) {
        return doLogAction(userPrincipal, action, resourceType, resourceId, null);
    }

    private Mono<Void> doLogAction(String userPrincipal, String action, String resourceType, UUID resourceId, Map<String, Object> details) {
        DatabaseClient.GenericExecuteSpec executeSpec;
        if (details != null && !details.isEmpty()) {
            Json detailsJson = jsonUtils.toJson(details);
            executeSpec = databaseClient.sql("INSERT INTO audit_logs (timestamp, user_principal, action, resource_type, resource_id, details) VALUES (:ts, :up, :act, :rt, :rid, :det)")
                    .bind("ts", OffsetDateTime.now())
                    .bind("up", userPrincipal != null ? userPrincipal : "SYSTEM")
                    .bind("act", action)
                    .bind("rt", resourceType)
                    .bind("rid", resourceId != null ? resourceId.toString() : null)
                    .bind("det", detailsJson);
        } else {
            executeSpec = databaseClient.sql("INSERT INTO audit_logs (timestamp, user_principal, action, resource_type, resource_id) VALUES (:ts, :up, :act, :rt, :rid)")
                    .bind("ts", OffsetDateTime.now())
                    .bind("up", userPrincipal != null ? userPrincipal : "SYSTEM")
                    .bind("act", action)
                    .bind("rt", resourceType)
                    .bind("rid", resourceId != null ? resourceId.toString() : null);
        }
        return executeSpec
                .then()
                .doOnError(e -> log.error("Failed to log audit action {}: {}", action, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
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