package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@JsonTypeName("replace")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplaceAudit extends AuditLogDetails {

    public ReplaceAudit(String filename) {
        this(filename, null);
    }

    public ReplaceAudit(Map<String, Object> metadata) {
        this(null, metadata);
    }

    @Schema(description = "New file replacing the existing one")
    private final String filename;

    @Schema(description = "New metadata replacing the existing ones")
    private final Map<String, Object> metadata;

}
