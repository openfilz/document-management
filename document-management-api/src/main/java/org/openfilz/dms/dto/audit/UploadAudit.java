package org.openfilz.dms.dto.audit;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.UUID;

@JsonTypeName("upload")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadAudit extends AuditLogDetails {
    private final String filename;
    private final UUID parentFolderId;
    private final Map<String, Object> metadata;
}
