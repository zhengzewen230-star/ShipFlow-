package com.shipflow.security;

import com.shipflow.security.authorization.CurrentCallerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import java.io.IOException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationManagers;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decoder,
                                                   ObjectProvider<CurrentCallerService> callerService,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = jsonEntryPoint("COMMON-1002", "Authentication required");
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        http.csrf(config -> config.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/_test/**")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/tenants").access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:create")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants").access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/tenants/**").access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:read")))
                        .requestMatchers("/api/v1/platform/tenants/**").access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:manage")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/stores").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("store:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("store:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores/**").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("store:read")))
                        .requestMatchers("/api/v1/stores/**").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("store:manage")))
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("user:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/roles", "/api/v1/roles/**").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("role:read")))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/roles/*/permissions").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("role:manage")))
                        .requestMatchers("/api/v1/permissions").access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("permission:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/platform/logistics-providers", "/api/v1/platform/logistics-providers/**",
                                "/api/v1/platform/logistics-channels", "/api/v1/platform/logistics-channels/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("logistics:read")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/platform/logistics-channels/*/price-rules")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("price-rule:manage")))
                        .requestMatchers("/api/v1/platform/logistics-providers/**", "/api/v1/platform/logistics-channels/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("logistics:manage")))
                        .requestMatchers("/api/v1/quotes", "/api/v1/quotes/**").hasAuthority("scope:TENANT")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(jsonDeniedHandler()));
        if (decoder.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth -> oauth
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .jwt(jwt -> jwt.decoder(decoder.getIfAvailable())
                            .jwtAuthenticationConverter(jwtAuthenticationConverter(callerService))));
        }
        return http.build();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        return repository;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter(ObjectProvider<CurrentCallerService> callerService) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            CurrentCallerService service = callerService.getIfAvailable();
            if (service == null) {
                return java.util.List.of();
            }
            Long userId;
            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (RuntimeException exception) {
                throw new org.springframework.security.authentication.BadCredentialsException("Invalid caller subject", exception);
            }
            String tenantClaim = jwt.getClaimAsString("tenant_id");
            Long tenantId = tenantClaim == null ? null : Long.valueOf(tenantClaim);
            return service.authorities(service.load(userId, tenantId));
        });
        return converter;
    }

    private AuthenticationEntryPoint jsonEntryPoint(String code, String message) {
        return (request, response, exception) -> writeError(response, 401, code, message);
    }
    private AccessDeniedHandler jsonDeniedHandler() {
        return (request, response, exception) -> writeError(response, 403,
                exception instanceof org.springframework.security.web.csrf.CsrfException ? "AUTH-1005" : "COMMON-1004",
                exception instanceof org.springframework.security.web.csrf.CsrfException ? "CSRF validation failed" : "Access denied");
    }
    private void writeError(jakarta.servlet.http.HttpServletResponse response, int status,
                            String code, String message) throws IOException {
        String traceId = com.shipflow.common.trace.TraceId.currentOrCreate();
        response.resetBuffer();
        response.setStatus(status);
        response.setHeader(com.shipflow.common.trace.TraceId.HEADER_NAME, traceId);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"traceId\":\"" +
                traceId + "\",\"error\":{\"code\":\"" + code +
                "\",\"message\":\"" + message + "\",\"details\":{}}}");
    }
}
