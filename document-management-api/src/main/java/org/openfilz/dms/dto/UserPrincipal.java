package org.openfilz.dms.dto;

public record UserPrincipal(String username, java.util.List<String> roles) {
} // For audit