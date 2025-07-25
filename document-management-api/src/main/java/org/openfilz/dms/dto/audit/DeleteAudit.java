package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@JsonTypeName("delete")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeleteAudit extends AuditLogDetails {
    private final UUID deletedParentFolderId;
}
