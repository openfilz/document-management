package org.openfilz.dms.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.openfilz.dms.enums.DocumentType;

import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Documents matching all provided criteria will be returned")
public record SearchByAuditLogRequest(
        @Schema(description = "Optional : Name of the file - if not provided or null : search all file names")
        String username,
        @Schema(description = "Document ID to search for")
        UUID id,
        @Schema(description = "Optional : Type of the document to search - if not provided or null : search all types")
        DocumentType type,
        @Schema(description = "Optional : UUID of the parent folder to search - if not provided or null : search in all folders")
        String action,
        @Schema(description = "Audit Metadata key-value pairs to search for")
        Map<String, Object> details
) {
}