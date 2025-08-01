package org.openfilz.dms.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.openfilz.dms.enums.DocumentType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.openfilz.dms.dto.JacksonConfig.OFFSET_DATE_TIME_PATTERN;

public record ListFolderRequest(
    UUID id,
    DocumentType type,
    String contentType,
    String name,
    String nameLike,
    Map<String, Object> metadata,
    Long size,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OFFSET_DATE_TIME_PATTERN)
    OffsetDateTime createdAtAfter,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OFFSET_DATE_TIME_PATTERN)
    OffsetDateTime createdAtBefore,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OFFSET_DATE_TIME_PATTERN)
    OffsetDateTime updatedAtAfter,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = OFFSET_DATE_TIME_PATTERN)
    OffsetDateTime updatedAtBefore,
    String createdBy,
    String updatedBy,
    @NotNull @Valid PageCriteria pageInfo
    ) {
}
