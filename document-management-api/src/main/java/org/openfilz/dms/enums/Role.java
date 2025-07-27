package org.openfilz.dms.enums;

public enum Role {
    AUDITOR, // Access to Audit trail
    CONTRIBUTOR, // Access to all endpoints
    READER // Access only to read-only endpoints
}
