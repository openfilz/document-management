// com/example/dms/utils/UserPrincipalExtractor.java
package org.openfilz.dms.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;


public class UserPrincipalExtractor {

    private static final String ANONYMOUS_USER = "anonymousUser";

    public static String getUsername(Authentication authentication) {
        if (authentication == null) {
            return ANONYMOUS_USER;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt) {
            return ((Jwt) principal).getSubject(); // 'sub' claim usually holds username
        }
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
            return jwtAuthToken.getToken().getSubject(); // Or getClaimAsString("preferred_username")
        }
        return authentication.getName();
    }
}