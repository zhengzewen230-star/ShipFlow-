package com.shipflow.tracking;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.tracking.api.ShipmentTrackingController;
import com.shipflow.tracking.api.model.ShipmentTrackingEventResponse;
import com.shipflow.tracking.application.ShipmentTrackingQueryApplicationService;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentTrackingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ShipmentTrackingControllerWebMvcTest.TestDependencies.class})
class ShipmentTrackingControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean ShipmentTrackingQueryApplicationService service;

    @TestConfiguration
    static class TestDependencies {
        @Bean JwtDecoder jwtDecoder() { return value -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void acceptsOrderOrWaybillReferenceForTenantTrackingReadAuthority() throws Exception {
        when(service.list(7L, 2L, "SF-001")).thenReturn(List.of(new ShipmentTrackingEventResponse(
                1L, 9L, "UAT-SF-1", "SF-001", "SF_PICKED_UP", "已收取快件", "received",
                "深圳集散中心", "SF_EXPRESS", OffsetDateTime.parse("2026-08-16T00:00:00Z"))));

        mvc.perform(get("/api/v1/orders/SF-001/tracking").with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                        .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("tracking:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].statusCode").value("SF_PICKED_UP"))
                .andExpect(jsonPath("$.data[0].source").value("SF_EXPRESS"));

        verify(service).list(7L, 2L, "SF-001");
    }

    @Test
    void rejectsUnauthenticatedTrackingLookup() throws Exception {
        mvc.perform(get("/api/v1/orders/UAT-SF-1/tracking"))
                .andExpect(status().isUnauthorized());
    }
}
