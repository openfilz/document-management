package org.openfilz.dms.dto.response;

import java.util.UUID;

public record FileResponse(UUID id, String name, UUID parentId, String contentType, Long size) {
}
