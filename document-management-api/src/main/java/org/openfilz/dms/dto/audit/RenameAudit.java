package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@JsonTypeName("rename")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RenameAudit extends AuditLogDetails {
    @Schema(description = "New name of the document")
    private final String name;
}
