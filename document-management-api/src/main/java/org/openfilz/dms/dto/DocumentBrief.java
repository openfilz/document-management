package org.openfilz.dms.dto;

import java.util.UUID;

public record DocumentBrief(UUID id, String name, String type) {
}