package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@JsonTypeName("updateMetadata")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateMetadataAudit extends AuditLogDetails {

    @Schema(description = "Updated Metadata")
    private final Map<String, Object> updatedMetadata;

}
