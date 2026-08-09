package com.shipflow.tenant;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.tenant.api.TenantController;
import com.shipflow.tenant.application.TenantApplicationService;
import com.shipflow.tenant.domain.model.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TenantControllerWebMvcTest.Config.class})
class TenantControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean TenantApplicationService service;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void anonymousPlatformTenantRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-1002"));
    }

    @Test
    void authenticatedCallerWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/platform/tenants").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-1004"));
    }

    @Test
    void platformTenantCreateAcceptsMatchingCsrfCookieAndHeader() throws Exception {
        when(service.create(any(), any(), any(), any())).thenReturn(new Tenant(
                101L, "IT_API_TENANT_TEST", "API验收测试租户", "ACTIVE", 0L,
                LocalDateTime.now(), LocalDateTime.now()));

        String token = "csrf-token";
        mockMvc.perform(post("/api/v1/platform/tenants")
                        .with(jwt().jwt(jwt -> jwt.subject("1")).authorities(
                                new SimpleGrantedAuthority("scope:PLATFORM"),
                                new SimpleGrantedAuthority("tenant:create")))
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token))
                        .header("X-XSRF-TOKEN", token)
                        .header("Idempotency-Key", "idempotency-test")
                        .contentType("application/json")
                        .content("{\"tenantCode\":\"IT_API_TENANT_TEST\",\"tenantName\":\"API验收测试租户\","
                                + "\"initialAdmin\":{\"username\":\"admin_test\",\"displayName\":\"管理员\","
                                + "\"temporaryPassword\":\"Tmp!Password123\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}
