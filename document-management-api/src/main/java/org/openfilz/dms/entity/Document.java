// com/example/dms/entity/Document.java
package org.openfilz.dms.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfilz.dms.enums.DocumentType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("documents")
public class Document {
    @Id
    private UUID id;

    private String name;

    @Column("type")
    private DocumentType type;

    @Column("content_type")
    private String contentType; // MIME type

    private Long size; // in bytes

    @Column("parent_id")
    private UUID parentId; // Null if root

    @Column("storage_path")
    private String storagePath; // Path in FS or object key in S3

    @Column("metadata")
    private Json metadata; // Stored as JSONB

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("created_by")
    private String createdBy;

    @Column("updated_by")
    private String updatedBy;
}