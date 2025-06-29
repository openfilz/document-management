package org.openfilz.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateMetadataRequest(
        @Schema(description = "Metadata key-value pairs to update or add.")
        java.util.Map<String, Object> metadataToUpdate
) {
}
