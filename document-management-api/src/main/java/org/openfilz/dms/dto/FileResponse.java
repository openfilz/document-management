package org.openfilz.dms.dto;

import java.util.UUID;

public record FileResponse(UUID id, String name, UUID parentId, String contentType, Long size) {
}
