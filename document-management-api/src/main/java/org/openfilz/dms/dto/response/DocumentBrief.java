package org.openfilz.dms.dto.response;

import java.util.UUID;

public record DocumentBrief(UUID id, String name, String type) {
}