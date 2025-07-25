package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@JsonTypeName("deleteMetadata")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteMetadataAudit extends AuditLogDetails {

    @Schema(description = "List of deleted metadata keys")
    private final List<String> deletedMetadataKeys;

}
