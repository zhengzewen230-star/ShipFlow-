package com.shipflow.onboarding;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.onboarding.api.OnboardingController;
import com.shipflow.onboarding.api.model.GuestEstimateResponse;
import com.shipflow.onboarding.application.OnboardingApplicationService;
import com.shipflow.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OnboardingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OnboardingControllerWebMvcTest.Config.class})
class OnboardingControllerWebMvcTest {
    @Autowired MockMvc mockMvc; @MockBean OnboardingApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder(){ return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }
    @Test void guestEstimateIsPublicButCsrfProtected() throws Exception {
        when(service.submitEstimate(any(),eq("estimate-key"))).thenReturn(new GuestEstimateResponse("EST-1","RECEIVED",false,"non-formal", OffsetDateTime.parse("2026-08-13T00:00:00Z")));
        String body="{\"originCountry\":\"CN\",\"destinationCountry\":\"US\",\"transportMode\":\"AIR\",\"cargoType\":\"GENERAL\",\"cargoName\":\"Bluetooth headset\",\"weight\":\"1.000\",\"volume\":\"0.010000\",\"contactName\":\"Li\",\"businessEmail\":\"li@example.com\",\"contactPhone\":\"+8613800000000\"}";
        mockMvc.perform(post("/api/v1/public/estimate-requests").with(csrf()).header("Idempotency-Key","estimate-key").contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.formalQuote").value(false));
        mockMvc.perform(post("/api/v1/public/estimate-requests").header("Idempotency-Key","estimate-key").contentType("application/json").content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("AUTH-1005"));
    }
    @Test void estimateRejectsSameOriginAndDestination() throws Exception {
        String body="{\"originCountry\":\"CN\",\"destinationCountry\":\"CN\",\"transportMode\":\"AIR\",\"cargoType\":\"GENERAL\",\"cargoName\":\"Bluetooth headset\",\"weight\":\"1.000\",\"volume\":\"0.010000\",\"contactName\":\"Li\",\"businessEmail\":\"li@example.com\",\"contactPhone\":\"+8613800000000\"}";
        when(service.submitEstimate(any(),eq("another-key"))).thenThrow(new com.shipflow.onboarding.application.OnboardingException("ONBOARDING-1005",422));
        mockMvc.perform(post("/api/v1/public/estimate-requests").with(csrf()).header("Idempotency-Key","another-key").contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity());
    }
    @Test void tenantCannotReadPlatformOnboardingApplications() throws Exception { mockMvc.perform(get("/api/v1/platform/onboarding-applications").with(jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("tenant:manage")))).andExpect(status().isForbidden()); }
    @Test void platformReviewerCanListApplications() throws Exception { when(service.list(null,1,20)).thenReturn(new com.shipflow.onboarding.domain.model.OnboardingPage(1,20,0,0,java.util.List.of())); mockMvc.perform(get("/api/v1/platform/onboarding-applications").with(jwt().jwt(j->j.subject("1")).authorities(new SimpleGrantedAuthority("scope:PLATFORM"),new SimpleGrantedAuthority("tenant:manage")))).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray()); }
}
