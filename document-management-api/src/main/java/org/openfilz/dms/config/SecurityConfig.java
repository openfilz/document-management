// com/example/dms/config/SecurityConfig.java
package org.openfilz.dms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
                                        .pathMatchers("/api/v1/**").authenticated() // Secure your API endpoints
                                        .anyExchange().authenticated();
                            }
                        }
                );
        if(!noAuth) {
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))); // Configure JWT decoder
        }
        return http.build();
    }


}