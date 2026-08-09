package com.shipflow.store;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.store.api.StoreController;
import com.shipflow.store.application.StoreApplicationService;
import com.shipflow.store.domain.model.Store;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
