package com.shipflow.operations;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.operations.api.OperationsController;
import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsWorkbenchQuery;
import com.shipflow.operations.api.model.OperationsWorkbenchResponse;
import com.shipflow.security.SecurityConfig;
import org.mybatis.spring.MyBatisSystemException;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperationsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, OperationsWorkbenchControllerWebMvcTest.Config.class})
class OperationsWorkbenchControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean OperationsQueryApplicationService service;

    @TestConfiguration
    static class Config {
        @Bean JwtDecoder jwtDecoder() { return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; }
    }

    @Test
    void passesTenantUserAndValidatedQueryToWorkbenchService() throws Exception {
        when(service.workbench(eq(7L), eq(2L), any(OperationsWorkbenchQuery.class)))
                .thenReturn(new OperationsWorkbenchResponse("Asia/Shanghai",
                        new OperationsWorkbenchResponse.Scope(7L, List.of(11L), "AUTHORIZED_STORES"),
                        new OperationsWorkbenchResponse.TimeRange("TODAY",
                                OffsetDateTime.parse("2026-08-16T16:00:00Z"), OffsetDateTime.parse("2026-08-17T16:00:00Z")),
                        OffsetDateTime.parse("2026-08-17T02:30:00Z"), List.of(), List.of(), List.of(), List.of()));

        mvc.perform(get("/api/v1/operations/workbench")
                        .param("timeRange", "TODAY")
                        .param("page", "2")
                        .param("pageSize", "25")
                        .param("sortBy", "updatedAt")
                        .param("sortDirection", "ASC")
                        .param("riskLimit", "15")
                        .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("operations:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessTimeZone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.scope.tenantId").value(7))
                .andExpect(jsonPath("$.data.timeRange.preset").value("TODAY"));

        verify(service).workbench(eq(7L), eq(2L), argThat(query -> query.page() == 2
                && query.pageSize() == 25 && query.riskLimit() == 15 && query.sortDirection().equals("ASC")));
    }

    @Test
    void platformScopeCannotReadWorkbench() throws Exception {
        mvc.perform(get("/api/v1/operations/workbench")
                        .with(jwt().jwt(j -> j.subject("1"))
                                .authorities(new SimpleGrantedAuthority("scope:PLATFORM"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void tenantWithoutOperationsPermissionCannotReadWorkbench() throws Exception {
        mvc.perform(get("/api/v1/operations/workbench")
                        .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedStoreIdWithUnifiedBadRequest() throws Exception {
        mvc.perform(get("/api/v1/operations/workbench")
                        .param("storeId", "not-a-number")
                        .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"), new SimpleGrantedAuthority("operations:read"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-1001"));
        verifyNoInteractions(service);
    }

    @Test
    void mapperFailureRemainsAnExplicitInternalBusinessError() throws Exception {
        when(service.workbench(eq(7L), eq(2L), any(OperationsWorkbenchQuery.class)))
                .thenThrow(new MyBatisSystemException(new IllegalStateException("mapper failure")));

        mvc.perform(get("/api/v1/operations/workbench")
                        .with(jwt().jwt(j -> j.subject("2").claim("tenant_id", "7"))
                                .authorities(new SimpleGrantedAuthority("scope:TENANT"),
                                        new SimpleGrantedAuthority("operations:read"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON-1007"));
    }
}
