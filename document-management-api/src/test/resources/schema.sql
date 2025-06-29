DROP TABLE IF EXISTS documents;

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    parent_id UUID,
    tenant_id VARCHAR(255) NOT NULL,
    path TEXT, -- Using TEXT for potentially long paths
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    modified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    modified_by VARCHAR(255),
    content_type VARCHAR(255),
    size BIGINT,
    storage_path VARCHAR(2048), -- Increased length
    metadata JSON -- H2 supports JSON type. JSONB specific operators might not work.
);

CREATE INDEX IF NOT EXISTS idx_documents_parent_id ON documents (parent_id);
CREATE INDEX IF NOT EXISTS idx_documents_tenant_id ON documents (tenant_id);
CREATE INDEX IF NOT EXISTS idx_documents_name ON documents (name);
CREATE INDEX IF NOT EXISTS idx_documents_type ON documents (type);
CREATE INDEX IF NOT EXISTS idx_documents_path ON documents (path);
-- CREATE INDEX IF NOT EXISTS idx_documents_metadata ON documents USING GIN (metadata); -- GIN not supported in H2

-- Add a constraint for unique name within a parent folder for a given type and tenant
-- This is more complex to enforce directly in H2 schema for NULL parent_id representing root
-- For simplicity, the application layer uniqueness checks are relied upon,
-- but for a real DB, a conditional unique index or function-based index might be used.
-- CREATE UNIQUE INDEX IF NOT EXISTS uq_name_parent_tenant_type ON documents (name, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'), tenant_id, type);
-- The COALESCE trick for NULL parent_id in unique constraint is DB specific. H2 might need a computed column or other workaround.
-- For testing, relying on service layer logic or specific test data setup is often sufficient.
