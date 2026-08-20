package com.shipflow.order;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.order.api.PriceConfirmationController;
import com.shipflow.order.api.model.PriceConfirmationView;
import com.shipflow.order.application.PriceConfirmationApplicationService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PriceConfirmationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PriceConfirmationControllerWebMvcTest.TestBeans.class})
class PriceConfirmationControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean PriceConfirmationApplicationService service;

    @TestConfiguration
    static class TestBeans {
        @Bean JwtDecoder jwtDecoder() {
            return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); };
        }
    }

    @Test
    void merchantOperatorCanSubmitRequestButCannotConfirm() throws Exception {
        when(service.request(eq(7L), eq(2L), eq(9L), any(), eq("request-1"), eq("trace-1")))
                .thenReturn(view("REQUESTED", "PENDING_PRICE_CONFIRMATION", 4L));

        mvc.perform(post("/api/v1/orders/9/price-confirmation-requests")
                        .with(token("order:price-request"))
                        .with(csrf())
                        .header("Idempotency-Key", "request-1")
                        .header("X-Request-Id", "trace-1")
                        .contentType("application/json")
                        .content("{\"feeAdjustmentId\":41,\"expectedFee\":12.50,\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmationStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.currentStatus").value("PENDING_PRICE_CONFIRMATION"));
        verify(service).request(eq(7L), eq(2L), eq(9L), any(), eq("request-1"), eq("trace-1"));

        mvc.perform(post("/api/v1/orders/9/price-confirmation")
                        .with(token("order:price-request"))
                        .with(csrf())
                        .header("Idempotency-Key", "confirm-1")
                        .contentType("application/json")
                        .content("{\"feeAdjustmentId\":41,\"expectedFee\":12.50,\"version\":4}"))
                .andExpect(status().isForbidden());
        verifyNoMoreInteractions(service);
    }

    @Test
    void financeCanConfirmAndReturnsReadyForOutbound() throws Exception {
        when(service.confirm(eq(7L), eq(6L), eq(9L), any(), eq("confirm-1"), eq("trace-2")))
                .thenReturn(view("CONFIRMED", "READY_FOR_OUTBOUND", 5L));

        mvc.perform(post("/api/v1/orders/9/price-confirmation")
                        .with(token("order:price-confirm", "6"))
                        .with(csrf())
                        .header("Idempotency-Key", "confirm-1")
                        .header("X-Request-Id", "trace-2")
                        .contentType("application/json")
                        .content("{\"feeAdjustmentId\":41,\"expectedFee\":12.50,\"version\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.currentStatus").value("READY_FOR_OUTBOUND"))
                .andExpect(jsonPath("$.data.version").value(5));
        verify(service).confirm(eq(7L), eq(6L), eq(9L), any(), eq("confirm-1"), eq("trace-2"));
    }

    @Test
    void tenantOrderReaderCanReadConfirmationStatus() throws Exception {
        when(service.get(7L, 2L, 9L)).thenReturn(view("PENDING_CONFIRMATION", "PENDING_PRICE_CONFIRMATION", 4L));

        mvc.perform(get("/api/v1/orders/9/price-confirmation").with(token("order:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(3))
                .andExpect(jsonPath("$.data.currentStatus").value("PENDING_PRICE_CONFIRMATION"));
        verify(service).get(7L, 2L, 9L);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String permission) {
        return token(permission, "2");
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor token(String permission, String userId) {
        return jwt().jwt(jwt -> jwt.subject(userId).claim("tenant_id", "7"))
                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority(permission));
    }

    private PriceConfirmationView view(String confirmationStatus, String currentStatus, long version) {
        return new PriceConfirmationView(9L, "SO-9", 8L, 3L, currentStatus,
                new BigDecimal("10.00"), new BigDecimal("12.50"),
                "CONFIRMED".equals(confirmationStatus) ? new BigDecimal("12.50") : null,
                "CNY", new BigDecimal("2.000"), version, 41L, "INCREASE",
                new BigDecimal("10.00"), new BigDecimal("12.50"), new BigDecimal("2.50"),
                confirmationStatus, null,
                "CONFIRMED".equals(confirmationStatus) ? OffsetDateTime.parse("2026-08-17T00:00:00Z") : null);
    }

}
