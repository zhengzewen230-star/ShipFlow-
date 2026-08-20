package com.shipflow.tracking;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.security.SecurityConfig;
import com.shipflow.tracking.api.TrackingEventsController;
import com.shipflow.tracking.api.model.TrackingEventViewResponse;
import com.shipflow.tracking.api.model.TrackingPageResponse;
import com.shipflow.tracking.application.TrackingQueryApplicationService;
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

@WebMvcTest(TrackingEventsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TrackingEventsControllerWebMvcTest.Config.class})
class TrackingEventsControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean TrackingQueryApplicationService service;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() {
            return value -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); };
        }
    }

    @Test
    void tenantReaderCanListEvents() throws Exception {
        when(service.page(7L, 2L, 9L, 1, 20)).thenReturn(new TrackingPageResponse(1, 20, 1, 1,
                List.of(new TrackingEventViewResponse(1L, "T-1", "EV-1", "PICKED", "Handed to carrier",
                        OffsetDateTime.parse("2026-01-01T01:00:00Z"), OffsetDateTime.parse("2026-01-01T01:01:00Z"), "PROCESSED"))));

        mvc.perform(get("/api/v1/orders/9/tracking-events")
                        .param("page", "1").param("pageSize", "20")
                        .with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("tracking:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventId").value("EV-1"));
        verify(service).page(7L, 2L, 9L, 1, 20);
    }

    @Test
    void platformOrMissingPermissionCannotListEvents() throws Exception {
        mvc.perform(get("/api/v1/orders/9/tracking-events")
                        .with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:PLATFORM"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/orders/9/tracking-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapsTenantSafeNotFound() throws Exception {
        when(service.page(7L, 2L, 9L, 1, 20)).thenThrow(new ShipmentOrderException("COMMON-1006", 404));
        mvc.perform(get("/api/v1/orders/9/tracking-events")
                        .with(jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("tracking:read"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON-1006"));
    }
}
