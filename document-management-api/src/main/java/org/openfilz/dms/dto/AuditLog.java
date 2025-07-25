
package org.openfilz.dms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import org.openfilz.dms.enums.AuditAction;
import org.openfilz.dms.enums.DocumentType;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLog(
        UUID id,
        OffsetDateTime timestamp,
        String username,
        AuditAction action,
        DocumentType resourceType,
        JsonNode details) {
}
