package org.openfilz.dms.service.impl;

import org.openfilz.dms.config.ApiVersion;
import org.openfilz.dms.service.SecurityService;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Arrays;

public abstract class AbstractSecurityService implements SecurityService {

    public static final String SLASH = "/";

    protected final boolean isWriteAccess(HttpMethod method, String path) {
        return ((method.equals(HttpMethod.DELETE) || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.PUT))
                && pathStartsWith(path, "/files", "/folders", "/documents")) ||
                (method.equals(HttpMethod.POST) && (
                        pathStartsWith(path, "/files", "/documents/upload", "/documents/upload-multiple") ||
                                path.equals("/folders") ||
                                path.equals("/folders/move") ||
                                path.equals("/folders/copy")));
    }

    protected final boolean isAudit(String path) {
        return pathStartsWith(path, "/audit");
    }

    /**
     * All GET methods and all POST methods used for search & query
     * */
    protected final boolean isQueryOrSearch(HttpMethod method, String path) {
        return (method.equals(HttpMethod.GET)
                && pathStartsWith(path, "/files", "/folders", "/documents"))
                ||
                (method.equals(HttpMethod.POST) && (
                        pathStartsWith(path, "/documents/download-multiple", "/documents/search/ids-by-metadata", "/folders/list")
                                || (path.startsWith("/documents/") && path.endsWith("/search/metadata")))
                );
    }

    protected String getPath(ServerHttpRequest request) {
        String path = request.getPath().value();
        int i = path.indexOf(ApiVersion.API_PREFIX);
        return path.substring(i + ApiVersion.API_PREFIX.length());
    }

    private boolean pathStartsWith(String path, String... contextPaths) {
        return Arrays.stream(contextPaths).anyMatch(contextPath -> pathStartsWith(path, contextPath));
    }

    private boolean pathStartsWith(String path, String contextPath) {
        return path.equals(contextPath) || path.startsWith(contextPath + SLASH);
    }

}
