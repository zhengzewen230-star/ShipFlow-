package com.shipflow.sf;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.sf.api.SfInternationalController;
import com.shipflow.sf.api.model.SfOperationResponse;
import com.shipflow.sf.application.SfInternationalService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SfInternationalController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SfInternationalControllerWebMvcTest.TestBeans.class})
class SfInternationalControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean SfInternationalService service;

    @TestConfiguration
    static class TestBeans {
        @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void warehouseOperatorCanSubmitProviderOperationThroughBackend() throws Exception {
        when(service.execute(eq(7L), eq(31L), eq(com.shipflow.sf.api.model.SfOperation.CREATE_ORDER),
                eq("sf-test-request"), eq("{}")))
                .thenReturn(new SfOperationResponse("CREATE_ORDER", "COM_RECE_IUOP_CREATE_ORDER", "sf-test-request", "SUBMITTED", null, null));

        mvc.perform(post("/api/v1/orders/31/sf-international/CREATE_ORDER")
                        .with(jwt().jwt(jwt -> jwt.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("warehouse:manage")))
                        .with(csrf())
                        .header("Idempotency-Key", "sf-test-request")
                        .contentType("application/json")
                        .content("{\"msgData\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        verify(service).execute(7L, 31L, com.shipflow.sf.api.model.SfOperation.CREATE_ORDER, "sf-test-request", "{}");
    }

    @Test
    void nonWarehouseTenantUserIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/orders/31/sf-international/CREATE_ORDER")
                        .with(jwt().jwt(jwt -> jwt.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("order:read")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"msgData\":\"{}\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
