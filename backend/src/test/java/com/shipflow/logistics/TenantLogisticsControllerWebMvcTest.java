package com.shipflow.logistics;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.logistics.api.TenantLogisticsController;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.PublicLogisticsChannel;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@WebMvcTest(TenantLogisticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TenantLogisticsControllerWebMvcTest.Config.class})
class TenantLogisticsControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean LogisticsMasterApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }
    @Test void tenantReadPermissionCanAccessPublicCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels").param("channelCode", "SF").param("serviceCountry", "US").param("sortField", "channelName").param("sortDirection", "ASC").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")).authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:read")))).andExpect(status().isOk());
    }
    @Test void serviceCountriesReturnsCountryCodeList() throws Exception {
        when(service.publicChannel(9L)).thenReturn(new PublicLogisticsChannel(9L, "Provider", "C1", "Channel", com.shipflow.logistics.domain.model.LogisticsChannel.TransportMode.AIR, List.of("US", "CA"), 1, null, null, null, "ACTIVE", List.of()));
        mockMvc.perform(get("/api/v1/logistics/channels/9/service-countries").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")).authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("US")).andExpect(jsonPath("$.data[1]").value("CA"));
    }
    @Test void tenantCallerWithoutLogisticsReadCannotAccessCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                .authorities(new SimpleGrantedAuthority("scope:TENANT")))).andExpect(status().isForbidden());
    }
    @Test void platformCallerCannotUseTenantCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels").with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("scope:PLATFORM"), new SimpleGrantedAuthority("logistics:read")))).andExpect(status().isForbidden());
    }
    @Test void tenantPriceRuleEndpointIsNotExposedAndOpenApiDoesNotAdvertiseIt() throws Exception {
        mockMvc.perform(get("/api/v1/logistics/channels/9/price-rule").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("logistics:read")))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON-1006"));
        String openApi = Files.readString(Path.of("../openapi/shipflow-api.yaml"), StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(openApi).doesNotContain("/logistics/channels/{channelId}/price-rule:");
    }
}
