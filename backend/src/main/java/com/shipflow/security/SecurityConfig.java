package com.shipflow.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.MediaType;
import java.io.IOException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decoder) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = jsonEntryPoint("COMMON-1002", "Authentication required");
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName("XSRF-TOKEN");
        csrf.setHeaderName("X-XSRF-TOKEN");
        csrf.setCookiePath("/api/v1/auth");
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        http.csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(requestHandler)
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/_test/**")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(jsonDeniedHandler()));
        if (decoder.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth -> oauth
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .jwt(jwt -> jwt.decoder(decoder.getIfAvailable())));
        }
        return http.build();
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
