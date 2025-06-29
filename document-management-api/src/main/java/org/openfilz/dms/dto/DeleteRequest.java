package org.openfilz.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record DeleteRequest(@Schema(description = "List of document IDs to delete.") java.util.List<UUID> documentIds) {
}
