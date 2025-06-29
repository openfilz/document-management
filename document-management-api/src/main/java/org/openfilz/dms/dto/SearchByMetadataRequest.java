package org.openfilz.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SearchByMetadataRequest(
        @Schema(description = "Metadata key-value pairs to search for. Documents matching all criteria will be returned.")
        java.util.Map<String, Object> metadataCriteria
) {
}