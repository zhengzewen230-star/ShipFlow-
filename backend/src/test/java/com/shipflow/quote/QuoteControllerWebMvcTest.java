package com.shipflow.quote;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.quote.api.QuoteController;
import com.shipflow.quote.api.model.QuotePageResponse;
import com.shipflow.quote.api.model.QuoteResponse;
import com.shipflow.quote.api.model.QuoteValidationResponse;
import com.shipflow.quote.application.QuoteException;
import com.shipflow.quote.application.QuoteQueryApplicationService;
import com.shipflow.quote.application.QuoteCreationApplicationService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuoteController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, QuoteControllerWebMvcTest.Config.class})
class QuoteControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean QuoteQueryApplicationService service;
    @MockBean QuoteCreationApplicationService creationService;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() {
            return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); };
        }
    }

    @Test
    void listQuotesPassesJwtTenantAndFilters() throws Exception {
        when(service.list(eq(7L), eq(2L), isNull(), eq(3L), eq(4L), isNull(), eq("VALID"), isNull(), isNull(), isNull(), isNull(), eq("createdAt"), eq("DESC"), eq(1), eq(20)))
                .thenReturn(new QuotePageResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/v1/quotes?storeId=3&channelId=4&status=VALID")
                        .with(tenantJwt("quote:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(service).list(7L, 2L, null, 3L, 4L, null, "VALID", null, null, null, null, "createdAt", "DESC", 1, 20);
    }

    @Test
    void getQuoteReturnsUnifiedNotFoundCode() throws Exception {
        when(service.get(7L, 2L, 99L)).thenThrow(new QuoteException("COMMON-1006", 404));

        mockMvc.perform(get("/api/v1/quotes/99").with(tenantJwt("quote:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON-1006"));
    }

    @Test
    void validateQuoteReturnsEligibilityFields() throws Exception {
        when(service.validate(7L, 2L, 11L))
                .thenReturn(new QuoteValidationResponse(11L, true, false, true, "AVAILABLE"));

        mockMvc.perform(post("/api/v1/quotes/11/validate").with(tenantJwt("quote:validate")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.expired").value(false))
                .andExpect(jsonPath("$.data.canCreateOrder").value(true))
                .andExpect(jsonPath("$.data.reason").value("AVAILABLE"));
    }

    @Test
    void createQuoteRequiresTenantScopeAndReturnsCreated() throws Exception {
        when(creationService.create(eq(7L), eq(2L), any(), eq("quote-test-key"), eq("request-1")))
                .thenReturn(new QuoteResponse(11L, "Q1", 3L, 4L, "US", 2, null, null, null, null,
                        null, null, new java.math.BigDecimal("12.00"), "USD", java.util.Map.of(),
                        java.time.OffsetDateTime.parse("2026-08-11T08:00:00Z"), java.time.OffsetDateTime.parse("2026-08-11T08:30:00Z"), "VALID", 0L));

        mockMvc.perform(post("/api/v1/quotes").with(tenantJwt("quote:create")).with(csrf()).header("Idempotency-Key", "quote-test-key")
                        .header("X-Request-Id", "request-1").contentType("application/json")
                        .content("{\"storeId\":3,\"channelId\":4,\"declaredWeight\":1.2,\"declaredLength\":20,\"declaredWidth\":20,\"declaredHeight\":20,\"destinationCountry\":\"US\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void platformScopeCannotQueryTenantQuotes() throws Exception {
        mockMvc.perform(get("/api/v1/quotes").with(jwt().jwt(token -> token.subject("1"))
                        .authorities(new SimpleGrantedAuthority("scope:PLATFORM"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-1004"));
    }

    @Test
    void platformScopeCannotCreateTenantQuotes() throws Exception {
        mockMvc.perform(post("/api/v1/quotes").with(jwt().jwt(token -> token.subject("1"))
                        .authorities(new SimpleGrantedAuthority("scope:PLATFORM")))
                        .with(csrf()).header("Idempotency-Key", "quote-platform-key")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-1004"));
    }

    @Test
    void tenantWithoutQuotePermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/quotes").with(tenantJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-1004"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor tenantJwt(String permission) {
        return jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority(permission));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor tenantJwt() {
        return jwt().jwt(token -> token.subject("2").claim("tenant_id", "7"))
                .authorities(new SimpleGrantedAuthority("scope:TENANT"));
    }
}
