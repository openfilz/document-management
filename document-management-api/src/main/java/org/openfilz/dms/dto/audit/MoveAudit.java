package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@JsonTypeName("move")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveAudit extends AuditLogDetails {
    private final UUID targetFolderId;
}
