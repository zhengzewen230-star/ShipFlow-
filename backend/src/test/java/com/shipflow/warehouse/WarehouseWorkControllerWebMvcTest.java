package com.shipflow.warehouse;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.warehouse.api.WarehouseWorkController;
import com.shipflow.warehouse.api.model.WarehouseWorkItemResponse;
import com.shipflow.warehouse.api.model.WarehouseWorkPageResponse;
import com.shipflow.warehouse.application.WarehouseWorkApplicationService;
import com.shipflow.warehouse.mapper.WarehouseWorkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WarehouseWorkController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WarehouseWorkControllerWebMvcTest.Config.class})
class WarehouseWorkControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean WarehouseWorkApplicationService service;
    @MockBean WarehouseWorkMapper mapper;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() { return value -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void warehouseOperatorCanListAndFilterWork() throws Exception {
        when(service.list(7L, "INBOUND", "TEST", 1, 20)).thenReturn(new WarehouseWorkPageResponse(1, 20, 1, 1, List.of()));
        mvc.perform(get("/api/v1/warehouse/orders").param("status", "INBOUND").param("orderNo", "TEST")
                        .with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("warehouse:manage"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        verify(service).list(7L, "INBOUND", "TEST", 1, 20);
    }

    @Test
    void userWithoutWarehousePermissionIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/warehouse/orders")
                        .with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserIsRejected() throws Exception {
        mvc.perform(get("/api/v1/warehouse/orders")).andExpect(status().isUnauthorized());
    }
}
