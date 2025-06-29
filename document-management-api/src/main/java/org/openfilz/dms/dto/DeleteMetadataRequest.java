package org.openfilz.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteMetadataRequest(
        @Schema(description = "List of metadata keys to delete.")
        java.util.List<String> metadataKeysToDelete
) {
}
