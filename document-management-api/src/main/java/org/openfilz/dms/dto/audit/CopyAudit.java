package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@JsonTypeName("copy")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CopyAudit extends AuditLogDetails {

    public CopyAudit(UUID sourceFileId, UUID targetFolderId) {
        this(sourceFileId, targetFolderId, null);
    }

    private final UUID sourceFileId;
    private final UUID targetFolderId;
    private final UUID sourceFolderId;
}
