package org.openfilz.dms.dto.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.openfilz.dms.dto.request.CreateFolderRequest;

import java.util.UUID;

@JsonTypeName("createFolder")
@Getter
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateFolderAudit extends AuditLogDetails {

    public CreateFolderAudit(CreateFolderRequest request) {
        this(request, null  );
    }

    private final CreateFolderRequest request;
    private final UUID sourceFolderId;

}
