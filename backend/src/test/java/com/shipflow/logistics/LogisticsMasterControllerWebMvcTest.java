package com.shipflow.logistics;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.logistics.api.LogisticsMasterController;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.LogisticsProvider;
import com.shipflow.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogisticsMasterController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, LogisticsMasterControllerWebMvcTest.Config.class})
class LogisticsMasterControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean LogisticsMasterApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }
    @Test void providerCreateRequiresPlatformScopeAndPermission() throws Exception {
        when(service.createProvider(any(), any(), any(), any())).thenReturn(new LogisticsProvider(1L, "P1", "Provider", "ACTIVE", 0L, null, null));
        mockMvc.perform(post("/api/v1/platform/logistics-providers").with(jwt().jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("scope:PLATFORM"), new SimpleGrantedAuthority("logistics:manage")))
                .with(csrf()).header("Idempotency-Key", "provider-test-key").contentType("application/json").content("{\"providerCode\":\"P1\",\"providerName\":\"Provider\"}"))
                .andExpect(status().isCreated());
    }
    @Test void providerCreateRejectsTenantAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/platform/logistics-providers").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                        .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:manage")))
                .with(csrf()).contentType("application/json").content("{\"providerCode\":\"P1\",\"providerName\":\"Provider\"}"))
                .andExpect(status().isForbidden());
    }
}
