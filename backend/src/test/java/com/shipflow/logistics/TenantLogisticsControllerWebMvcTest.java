package com.shipflow.logistics;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.logistics.api.TenantLogisticsController;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.LogisticsChannel;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;
import java.util.List;

@WebMvcTest(TenantLogisticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TenantLogisticsControllerWebMvcTest.Config.class})
class TenantLogisticsControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean LogisticsMasterApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }
    @Test void tenantReadPermissionCanAccessPublicCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")).authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:read")))).andExpect(status().isOk());
    }
    @Test void serviceCountriesReturnsCountryCodeList() throws Exception {
        when(service.availableChannel(9L)).thenReturn(new LogisticsChannel(9L, 2L, "C1", "Channel", LogisticsChannel.TransportMode.AIR, "Global", "ACTIVE", 0L, List.of("US", "CA"), null, null));
        mockMvc.perform(get("/api/v1/logistics/channels/9/service-countries").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")).authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("US")).andExpect(jsonPath("$.data[1]").value("CA"));
    }
    @Test void platformCallerCannotUseTenantCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels").with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("scope:PLATFORM"), new SimpleGrantedAuthority("logistics:read")))).andExpect(status().isForbidden());
    }
}
