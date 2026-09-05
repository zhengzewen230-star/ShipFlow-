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
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/_test/**"),
                                new AntPathRequestMatcher("/api/v1/integrations/logistics/*/tracking-events", "POST")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/estimate-requests", "/api/v1/public/onboarding-applications", "/api/v1/public/onboarding-activations").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/logistics/*/tracking-events").permitAll()
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .requestMatchers("/api/v1/platform/onboarding-applications", "/api/v1/platform/onboarding-applications/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:manage")))
                        .requestMatchers("/api/v1/platform/guest-estimate-leads", "/api/v1/platform/guest-estimate-leads/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("tenant:manage")))
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
                        .requestMatchers("/api/v1/logistics/channels", "/api/v1/logistics/channels/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("logistics:read")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/quotes/*/shipment-orders")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:create")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/quotes", "/api/v1/quotes/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("quote:read")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/quotes/*/validate")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("quote:validate")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/quotes")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("quote:create")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/inbound", "/api/v1/orders/*/measurements", "/api/v1/orders/*/outbound")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("warehouse:manage")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/sf-international/*")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("warehouse:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/price-confirmation")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), AuthorizationManagers.anyOf(
                                hasAuthority("order:read"), hasAuthority("order:price-request"), hasAuthority("order:price-confirm"))))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/price-confirmation-requests")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:price-request")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/price-confirmation")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:price-confirm")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/exceptions", "/api/v1/exceptions/**", "/api/v1/claims/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("exception:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/exceptions", "/api/v1/exceptions/**", "/api/v1/claims/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("exception:read")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/import-batches")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("finance:bill-import")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/billing", "/api/v1/billing/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), AuthorizationManagers.anyOf(
                                hasAuthority("billing:read"), hasAuthority("finance:bill-import"), hasAuthority("finance:reconcile"))))
                        .requestMatchers(HttpMethod.POST, "/api/v1/reconciliations/*/confirm", "/api/v1/reconciliations/*/reject", "/api/v1/reconciliations/*/comments")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("finance:reconcile")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/reconciliations", "/api/v1/reconciliations/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), AuthorizationManagers.anyOf(
                                hasAuthority("billing:read"), hasAuthority("finance:bill-import"), hasAuthority("finance:reconcile"))))
                        .requestMatchers("/api/v1/platform/audit-logs", "/api/v1/platform/audit-logs/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:PLATFORM"), hasAuthority("audit:read")))
                        .requestMatchers("/api/v1/audit-logs", "/api/v1/audit-logs/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("audit:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/operations", "/api/v1/operations/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("operations:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/warehouse/overview")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("warehouse:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/warehouse/orders", "/api/v1/warehouse/orders/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("warehouse:manage")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/shipment-orders/*/tracking", "/api/v1/shipment-orders/*/tracking/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("tracking:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/tracking-events")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("tracking:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/*/tracking")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("tracking:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking-events")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("tracking:read")))
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/**")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:read")))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/orders/*")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:manage")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/submit", "/api/v1/orders/*/cancel")
                        .access(AuthorizationManagers.allOf(hasAuthority("scope:TENANT"), hasAuthority("order:manage")))
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
