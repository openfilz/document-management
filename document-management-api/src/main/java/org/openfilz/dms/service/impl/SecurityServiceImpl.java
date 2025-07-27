package org.openfilz.dms.service.impl;

import lombok.RequiredArgsConstructor;
import org.openfilz.dms.config.ApiVersion;
import org.openfilz.dms.enums.Role;
import org.openfilz.dms.enums.RoleTokenLookup;
import org.openfilz.dms.service.SecurityService;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class SecurityServiceImpl implements SecurityService {

    private static final String ROLES = "roles";
    private static final String REALM_ACCESS = "realm_access";
    public static final String SLASH = "/";

    private final RoleTokenLookup roleTokenLookup;

    public boolean authorize(Authentication auth, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        HttpMethod method = request.getMethod();
        String path = getPath(request);
        if (isQueryOrSearch(method, path))
            return isAuthorized((JwtAuthenticationToken) auth, Role.READER, Role.CONTRIBUTOR);
        if(isAudit(path)) {
            return isAuthorized((JwtAuthenticationToken) auth, Role.AUDITOR);
        }
        if(isWriteAccess(method, path)) {
            return isAuthorized((JwtAuthenticationToken) auth, Role.CONTRIBUTOR);
        }
        return false;
    }

    private String getPath(ServerHttpRequest request) {
        String path = request.getPath().value();
        int i = path.indexOf(ApiVersion.API_PREFIX);
        return path.substring(i + ApiVersion.API_PREFIX.length());
    }

    private boolean isWriteAccess(HttpMethod method, String path) {
        return ((method.equals(HttpMethod.DELETE) || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.PUT))
                && pathStartsWith(path, "/files", "/folders", "/documents")) ||
                (method.equals(HttpMethod.POST) && (
                        pathStartsWith(path, "/files", "/documents/upload", "/documents/upload-multiple") ||
                                path.equals("/folders") ||
                                path.equals("/folders/move") ||
                                path.equals("/folders/copy")));
    }

    private boolean isAudit(String path) {
        return pathStartsWith(path, "/audit");
    }

    /**
     * All GET methods and all POST methods used for search & query
     * */
    private boolean isQueryOrSearch(HttpMethod method, String path) {
        if((method.equals(HttpMethod.GET)
                && pathStartsWith (path, "/files", "/folders", "/documents"))
                ||
                (method.equals(HttpMethod.POST) && (
                        pathStartsWith(path, "/documents/download-multiple", "/documents/search/ids-by-metadata", "/folders/list")
                                || (path.startsWith("/documents/") && path.endsWith("/search/metadata")))
                )) {
            return true;
        }
        return false;
    }

    private boolean isAuthorized(JwtAuthenticationToken auth, Role... requiredRoles) {
        if(roleTokenLookup == RoleTokenLookup.AUTHORITY) {
            return isInAuthorities(auth, requiredRoles);
        }
        return isInRealmRoles(auth, requiredRoles);
    }

    private boolean isInRealmRoles(JwtAuthenticationToken auth, Role... requiredRoles) {
        List<String> accessRoles = getAccessRoles(auth);
        return accessRoles != null && !accessRoles.isEmpty() && Arrays.stream(requiredRoles).anyMatch(requiredRole -> accessRoles.contains(requiredRole.toString()));
    }

    private List<String> getAccessRoles(JwtAuthenticationToken auth) {
        return getRealmAccessRoles(auth);
    }

    private List<String> getRealmAccessRoles(JwtAuthenticationToken auth) {
        Map<String, Object> tokenAttributes = auth.getTokenAttributes();
        if(tokenAttributes != null &&  tokenAttributes.containsKey(REALM_ACCESS)) {
            Map<String, List<String>> realmAccess = (Map<String, List<String>>) tokenAttributes.get(REALM_ACCESS);
            if(realmAccess != null && realmAccess.containsKey(ROLES)) {
                return realmAccess.get(ROLES);
            }
        }
        return null;
    }

    private boolean isInAuthorities(Authentication auth, Role... requiredRoles) {
        return auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(role -> Arrays.stream(requiredRoles).anyMatch(r->r.toString().equals(role.getAuthority())));
    }

    private boolean pathStartsWith(String path, String... contextPaths) {
        return Arrays.stream(contextPaths).anyMatch(contextPath -> pathStartsWith(path, contextPath));
    }

    private boolean pathStartsWith(String path, String contextPath) {
        return path.equals(contextPath) || path.startsWith(contextPath + SLASH);
    }
}
