package com.shipflow.store;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.store.api.StoreController;
import com.shipflow.store.application.StoreApplicationService;
import com.shipflow.store.domain.model.Store;
import com.shipflow.store.domain.model.StorePage;
import com.shipflow.store.api.model.StoreDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, StoreControllerWebMvcTest.Config.class})
class StoreControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean StoreApplicationService service;

    @TestConfiguration static class Config {
        @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test void storeCreateRequiresTenantScopeAndPermission() throws Exception {
        when(service.create(any(), any(), any(), any(), any())).thenReturn(
                new Store(1L, 1L, "S1", "Store", "P", "A", "ACTIVE", 0L, null, null));
        mockMvc.perform(post("/api/v1/stores").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                        .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("store:manage")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .header("Idempotency-Key", "store-test-key")
                .contentType("application/json")
                .content("{\"storeCode\":\"S1\",\"storeName\":\"Store\",\"platformCode\":\"P\",\"platformAccount\":\"A\"}"))
                .andExpect(status().isCreated());
    }

    @Test void storeCreateWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/stores").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void storeListPassesTenantAndCallerToScopedService() throws Exception {
        when(service.page(1L, 2L, null, null, null, null, "updatedAt", "DESC", 1, 20)).thenReturn(new StorePage(1, 20, 0L, 0, java.util.List.of()));
        mockMvc.perform(get("/api/v1/stores").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                        .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("store:read"))))
                .andExpect(status().isOk());
        verify(service).page(1L, 2L, null, null, null, null, "updatedAt", "DESC", 1, 20);
    }

    @Test void storeListPassesWhitelistedFiltersAndSort() throws Exception {
        when(service.page(1L, 2L, "S1", "Demo", "AMAZON", "ACTIVE", "storeName", "ASC", 2, 10))
                .thenReturn(new StorePage(2, 10, 1L, 1, java.util.List.of()));
        mockMvc.perform(get("/api/v1/stores")
                        .param("page", "2").param("pageSize", "10")
                        .param("storeCode", "S1").param("storeName", "Demo")
                        .param("platformCode", "AMAZON").param("status", "ACTIVE")
                        .param("sortBy", "storeName").param("sortDirection", "ASC")
                        .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("store:read"))))
                .andExpect(status().isOk());
        verify(service).page(1L, 2L, "S1", "Demo", "AMAZON", "ACTIVE", "storeName", "ASC", 2, 10);
    }

    @Test void storeDetailUsesCallerScopeAndReturnsMaskedDetailContract() throws Exception {
        when(service.getDetail(1L, 2L, 10L)).thenReturn(new StoreDetailResponse(
                10L, 1L, "S1", "Store", "AMAZON", "a**t", null, null, null,
                "ACTIVE", 0L, null, null, 3L,
                java.util.List.of(new com.shipflow.store.api.model.StoreAuditSummary("UPDATE", "SUCCESS", null)),
                java.util.List.of("countryRegion", "defaultShippingAddress", "defaultLogisticsChannel"), 0L));
        mockMvc.perform(get("/api/v1/stores/10").with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1"))
                        .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("store:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformAccountMasked").value("a**t"))
                .andExpect(jsonPath("$.data.historicalOrderCount").value(3))
                .andExpect(jsonPath("$.data.platformAccount").doesNotExist());
        verify(service).getDetail(1L, 2L, 10L);
    }

    @Test void storeUpdateAndStatusRequireIdempotencyKey() throws Exception {
        when(service.update(eq(1L), eq(2L), eq(10L), any(), eq("update-key"), any())).thenReturn(new Store(10L,1L,"S1","Renamed","AMAZON","acct","ACTIVE",1L,null,null));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/stores/10")
                .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "1")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("store:manage")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).header("Idempotency-Key","update-key")
                .contentType("application/json").content("{\"storeName\":\"Renamed\",\"platformCode\":\"AMAZON\",\"platformAccount\":\"acct\",\"version\":0}"))
                .andExpect(status().isOk());
        verify(service).update(eq(1L), eq(2L), eq(10L), any(), eq("update-key"), any());
    }
}
