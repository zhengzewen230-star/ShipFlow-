package com.shipflow.warehouse;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.warehouse.api.WarehouseOverviewController;
import com.shipflow.warehouse.application.WarehouseOverviewApplicationService;
import com.shipflow.warehouse.domain.WarehouseOverview;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WarehouseOverviewController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, WarehouseOverviewControllerWebMvcTest.Config.class})
class WarehouseOverviewControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean WarehouseOverviewApplicationService service;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() {
            return value -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); };
        }
    }

    @Test
    void warehouseOperatorCanReadOverview() throws Exception {
        when(service.overview(7L)).thenReturn(new WarehouseOverview(1, 2, 3, 4, 4, 5, 6, 7, 8, List.of(), List.of()));

        mvc.perform(get("/api/v1/warehouse/overview")
                        .with(jwt().jwt(token -> token.subject("warehouse-user").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("warehouse:manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingInbound").value(1))
                .andExpect(jsonPath("$.data.pendingMeasurement").value(2))
                .andExpect(jsonPath("$.data.pendingLabel").value(3))
                .andExpect(jsonPath("$.data.pendingHandover").value(4))
                .andExpect(jsonPath("$.data.recentOrders").isEmpty());
        verify(service).overview(7L);
    }

    @Test
    void tenantWithoutWarehousePermissionGetsForbidden() throws Exception {
        mvc.perform(get("/api/v1/warehouse/overview")
                        .with(jwt().jwt(token -> token.subject("tenant-user").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void emptyOverviewReturnsZeroCountsAndEmptyLists() throws Exception {
        when(service.overview(7L)).thenReturn(new WarehouseOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of()));

        mvc.perform(get("/api/v1/warehouse/overview")
                        .with(jwt().jwt(token -> token.subject("warehouse-user").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("warehouse:manage"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingInbound").value(0))
                .andExpect(jsonPath("$.data.todayOutbound").value(0))
                .andExpect(jsonPath("$.data.recentOrders").isEmpty())
                .andExpect(jsonPath("$.data.recentTrackingExceptions").isEmpty());
    }

    @Test
    void unauthenticatedRequestGetsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/warehouse/overview"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
