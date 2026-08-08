package com.shipflow.auth;

import com.shipflow.auth.api.AuthCookieProperties;
import com.shipflow.auth.api.AuthController;
import com.shipflow.auth.api.CurrentUserController;
import com.shipflow.auth.application.AuthApplicationService;
import com.shipflow.auth.application.AuthSessionResult;
import com.shipflow.auth.application.model.LoginResult;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.security.refresh.RefreshToken;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultMatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, CurrentUserController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthControllerWebMvcTest.TestConfig.class})
class AuthControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean AuthApplicationService service;

    @TestConfiguration
    static class TestConfig {
        @Bean AuthCookieProperties authCookieProperties() { AuthCookieProperties p = new AuthCookieProperties(); p.setSecure(false); return p; }

        @Bean
        JwtDecoder rejectingJwtDecoder() {
            return token -> {
                throw new BadJwtException("invalid test token");
            };
        }
    }

    @Test
    void csrfEndpointSetsReadableCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH-1005"));
    }

    @Test
    void missingXsrfCookieReturnsTraceIdOnCsrfFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", "token-without-cookie")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH-1005"))
                .andExpect(traceIdMatchesBody());
    }

    @Test
    void missingXsrfHeaderReturnsTraceIdOnCsrfFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH-1005"))
                .andExpect(traceIdMatchesBody());
    }

    @Test
    void mismatchedXsrfCookieAndHeaderReturnsTraceIdOnCsrfFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "cookie-token"))
                        .header("X-XSRF-TOKEN", "different-header-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH-1005"))
                .andExpect(traceIdMatchesBody());
    }

    private ResultMatcher traceIdMatchesBody() {
        return result -> {
            String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
            assertNotNull(headerTraceId);
            assertNotEquals("unknown", headerTraceId);
            String bodyTraceId = new ObjectMapper()
                    .readTree(result.getResponse().getContentAsString())
                    .path("traceId")
                    .asText();
            assertEquals(headerTraceId, bodyTraceId);
        };
    }

    @Test
    void loginSuccessReturnsAccessTokenAndRefreshCookie() throws Exception {
        var identity = new LoginIdentity(1L, 2L, "u", "User", LoginIdentity.Scope.TENANT, java.util.Set.of());
        var result = new AuthSessionResult(new LoginResult("test-access-token", "Bearer", 900, identity),
                new RefreshToken("test-refresh-token"));
        when(service.login(any())).thenReturn(result);
        mockMvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(cookie().httpOnly("REFRESH_TOKEN", true))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void loginFailureUsesAuthCode() throws Exception {
        when(service.login(any())).thenThrow(new LoginIdentityAuthenticationException());
        mockMvc.perform(post("/api/v1/auth/login").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-1001"));
    }

    @Test
    void refreshReturnsRotatedCookie() throws Exception {
        var identity = new LoginIdentity(1L, 2L, "u", "User", LoginIdentity.Scope.TENANT, java.util.Set.of());
        when(service.refresh("old")).thenReturn(new AuthSessionResult(
                new LoginResult("new-access", "Bearer", 900, identity), new RefreshToken("new-refresh")));
        mockMvc.perform(post("/api/v1/auth/refresh").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", "old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void logoutClearsCookiesAndIsSuccessful() throws Exception {
        doNothing().when(service).logout("old");
        mockMvc.perform(post("/api/v1/auth/logout").with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie("REFRESH_TOKEN", "old")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("REFRESH_TOKEN", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-1002"))
                .andExpect(traceIdMatchesBody());
    }

    @Test
    void meWithInvalidBearerTokenReturnsBusinessError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-1002"))
                .andExpect(traceIdMatchesBody());
    }

    @Test
    void meWithTamperedSignatureReturnsBusinessError() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.invalid-signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-1002"))
                .andExpect(traceIdMatchesBody());
    }

    @Test
    void meWithBearerIdentityReturnsCurrentUser() throws Exception {
        var identity = new LoginIdentity(1L, 2L, "u", "User", LoginIdentity.Scope.TENANT,
                java.util.Set.of("auth:read"));
        when(service.currentUser(any())).thenReturn(identity);
        mockMvc.perform(get("/api/v1/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("1").claim("tenant_id", "2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.tenantId").value("2"));
    }
}
