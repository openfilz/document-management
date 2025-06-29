package org.openfilz.dms.dto;

import java.util.UUID;

public record UploadResponse(UUID id, String name, String contentType, Long size) {
}
