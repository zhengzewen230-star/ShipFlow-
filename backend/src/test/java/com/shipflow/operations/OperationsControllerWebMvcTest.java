package com.shipflow.operations;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.operations.api.OperationsController;
import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsSummary;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperationsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OperationsControllerWebMvcTest.Config.class})
class OperationsControllerWebMvcTest {
    @Autowired MockMvc mvc; @MockBean OperationsQueryApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }
    @Test void summaryUsesTenantFromJwt() throws Exception {
        when(service.summary(7L)).thenReturn(new OperationsSummary(1,0,0,0,0,0,0,0,0,0));
        mvc.perform(get("/api/v1/operations/summary").with(jwt().jwt(j -> j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("operations:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.draft").value(1));
        verify(service).summary(7L);
    }
    @Test void platformScopeCannotReadTenantOperations() throws Exception {
        mvc.perform(get("/api/v1/operations/todos").with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("scope:PLATFORM"))))
                .andExpect(status().isForbidden());
    }
}
