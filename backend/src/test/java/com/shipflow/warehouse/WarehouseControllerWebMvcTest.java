package com.shipflow.warehouse;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.warehouse.api.WarehouseController;
import com.shipflow.warehouse.api.model.WarehouseResult;
import com.shipflow.warehouse.application.WarehouseApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WarehouseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WarehouseControllerWebMvcTest.TestBeans.class})
class WarehouseControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean WarehouseApplicationService service;

    @TestConfiguration
    static class TestBeans {
        @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void warehousePermissionCanConfirmInboundAndReceivesVersion() throws Exception {
        when(service.inbound(any(), any(), any(), any(), any(), any()))
                .thenReturn(new WarehouseResult(9L, "INBOUND", BigDecimal.ONE, BigDecimal.TEN, 4L));

        mvc.perform(post("/api/v1/orders/9/inbound")
                        .with(token("scope:TENANT", "warehouse:manage"))
                        .header("Idempotency-Key", "warehouse-inbound-test")
                        .header("X-Request-Id", "warehouse-test")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(9))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).inbound(any(), any(), any(), any(), any(), any());
    }

    @Test
    void warehousePermissionCanSaveMeasurementWithBackendContract() throws Exception {
        when(service.measure(any(), any(), any(), any(), any(), any()))
                .thenReturn(new WarehouseResult(9L, "READY_FOR_OUTBOUND", new BigDecimal("2.000"), BigDecimal.TEN, 4L));

        mvc.perform(post("/api/v1/orders/9/measurements")
                        .with(token("scope:TENANT", "warehouse:manage"))
                        .header("Idempotency-Key", "warehouse-measure-test")
                        .header("X-Request-Id", "warehouse-test")
                        .contentType("application/json")
                        .content("{\"actualWeight\":1.250,\"actualLength\":10,\"actualWidth\":20,\"actualHeight\":5,\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_OUTBOUND"))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).measure(any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidMeasurementIsRejectedWithChineseFieldReason() throws Exception {
        mvc.perform(post("/api/v1/orders/9/measurements")
                        .with(token("scope:TENANT", "warehouse:manage"))
                        .header("Idempotency-Key", "warehouse-invalid-measure-test")
                        .contentType("application/json")
                        .content("{\"actualWeight\":0,\"actualLength\":10,\"actualWidth\":20,\"actualHeight\":5,\"version\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-1001"))
                .andExpect(jsonPath("$.error.details.actualWeight").value("实际重量必须大于 0"));
        verifyNoInteractions(service);
    }

    @Test
    void nonWarehouseTenantUserGetsForbidden() throws Exception {
        mvc.perform(post("/api/v1/orders/9/inbound")
                        .with(token("scope:TENANT", "order:read"))
                        .header("Idempotency-Key", "warehouse-forbidden-test")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserGetsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/orders/9/inbound")
                        .with(csrf())
                        .header("Idempotency-Key", "warehouse-unauthenticated-test")
                        .contentType("application/json")
                        .content("{\"version\":3}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(String... authorities) {
        java.util.List<GrantedAuthority> granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .map(authority -> (GrantedAuthority) authority)
                .toList();
        return jwt().jwt(jwt -> jwt.subject("2").claim("tenant_id", "7"))
                .authorities(granted);
    }
}
