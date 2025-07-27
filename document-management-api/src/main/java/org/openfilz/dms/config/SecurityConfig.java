// com/example/dms/config/SecurityConfig.java
package org.openfilz.dms.config;

import org.openfilz.dms.enums.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity // For method-level security like @PreAuthorize
public class SecurityConfig {

    @Value("${spring.security.no-auth}")
    private Boolean noAuth;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    @ConditionalOnProperty(name = "spring.security.no-auth", havingValue = "false")
    public ReactiveJwtDecoder jwtDecoder() {
        return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }


    private static final String[] AUTH_WHITELIST = {
            // Swagger UI v3
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/swagger-ui/**",
            // Actuator health
            "/actuator/health/**"
    };

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Disable CSRF for stateless APIs
                .authorizeExchange(exchanges -> {
                            if (noAuth) {
                                exchanges.anyExchange().permitAll();
                            } else {
                                exchanges.pathMatchers(AUTH_WHITELIST).permitAll() // Whitelist Swagger and health
                                        .pathMatchers(ApiVersion.API_PREFIX + "/**")
                                            .access((mono, context) -> mono
                                                .map(auth -> newAuthorizationDecision(auth, context)))
                                        .anyExchange()
                                        .authenticated();
                            }
                        }
                );
        if(!noAuth) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))); // Configure JWT decoder
        }
        return http.build();
    }

    private AuthorizationDecision newAuthorizationDecision(Authentication auth, AuthorizationContext context) {
        return new AuthorizationDecision(authorize(auth, context));
    }

    private boolean authorize(Authentication auth, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();
        int i = path.indexOf(ApiVersion.API_PREFIX);
        path = path.substring(i + ApiVersion.API_PREFIX.length());
        if((method.equals(HttpMethod.GET)
            && pathStartsWith (path, "/files", "/folders", "/documents"))
                || pathStartsWith(path, "/documents/download-multiple", "/documents/search/ids-by-metadata", "/folders/list")
                || (path.startsWith("/documents/") && path.endsWith("/search/metadata"))) {
            return isAuthorized((JwtAuthenticationToken) auth, Role.READER, Role.CONTRIBUTOR);
        }
        if(pathStartsWith(path, "/audit")) {
            return isAuthorized((JwtAuthenticationToken) auth, Role.AUDITOR);
        }
        if(((method.equals(HttpMethod.DELETE) || method.equals(HttpMethod.PATCH) || method.equals(HttpMethod.PUT))
                && pathStartsWith (path, "/files", "/folders", "/documents")) ||
                pathStartsWith(path, "/files", "/documents/upload", "/documents/upload-multiple") ||
                path.equals(ApiVersion.API_PREFIX + "/folders") ||
                path.equals(ApiVersion.API_PREFIX + "/folders/move") ||
                path.equals(ApiVersion.API_PREFIX + "/folders/copy")) {
            return isAuthorized((JwtAuthenticationToken) auth, Role.CONTRIBUTOR);
        }

        return false;
    }

    private boolean isAuthorized(JwtAuthenticationToken auth, Role... requiredRoles) {
        return isInAuthorities(auth, requiredRoles) || isInRealmRoles(auth, requiredRoles);
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
        if(tokenAttributes != null &&  tokenAttributes.containsKey("realm_access")) {
            Map<String, List<String>> realmAccess = (Map<String, List<String>>) tokenAttributes.get("realm_access");
            if(realmAccess != null && realmAccess.containsKey("roles")) {
                return realmAccess.get("roles");
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
        return path.equals(contextPath) || path.startsWith(contextPath + "/");
    }


}