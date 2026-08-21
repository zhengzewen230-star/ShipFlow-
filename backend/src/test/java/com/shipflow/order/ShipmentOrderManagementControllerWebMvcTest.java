package com.shipflow.order;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.order.api.ShipmentOrderManagementController;
import com.shipflow.order.api.model.OrderDetailResponse;
import com.shipflow.order.api.model.ShipmentOrderPageResponse;
import com.shipflow.order.api.model.ShipmentOrderResponse;
import com.shipflow.order.application.ShipmentOrderExportApplicationService;
import com.shipflow.order.application.ShipmentOrderLifecycleApplicationService;
import com.shipflow.order.application.ShipmentOrderQueryApplicationService;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentOrderManagementController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ShipmentOrderManagementControllerWebMvcTest.Config.class})
class ShipmentOrderManagementControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean ShipmentOrderQueryApplicationService query;
    @MockBean ShipmentOrderLifecycleApplicationService lifecycle;
    @MockBean ShipmentOrderExportApplicationService exporter;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder(){ return token -> { throw new org.springframework.security.oauth2.jwt.BadJwtException("test"); }; } }

    @Test void listsTenantOrdersWithWhitelistedFilters() throws Exception {
        when(query.listFilteredForCaller(anyLong(), anyLong(), nullable(String.class), nullable(Long.class), nullable(String.class), nullable(String.class), nullable(Long.class), nullable(String.class), nullable(LocalDateTime.class), nullable(LocalDateTime.class), anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(new ShipmentOrderPageResponse(2,10,1,1,List.of(order())));
        mvc.perform(get("/api/v1/orders").param("orderNo","SO").param("status","DRAFT").param("storeId","3").param("page","2").param("pageSize","10").param("sortBy","orderNo").param("sortDirection","ASC").with(readToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("DRAFT")).andExpect(jsonPath("$.data.items[0].version").value(4));
    }

    @Test void returnsAggregatedDetailForAuthorizedCaller() throws Exception {
        when(query.getDetailForCaller(7L,2L,9L)).thenReturn(new OrderDetailResponse(order(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of()));
        mvc.perform(get("/api/v1/orders/9").with(readToken())).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(4)).andExpect(jsonPath("$.data.timeline").isArray());
    }

    @Test void rejectsPlatformScopeForTenantOrderList() throws Exception {
        mvc.perform(get("/api/v1/orders").with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("scope:PLATFORM")))).andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readToken(){ return jwt().jwt(j -> j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("order:read")); }
    private ShipmentOrderResponse order(){ return new ShipmentOrderResponse(9L,"SO9",8L,"DRAFT",new BigDecimal("12.50"),"USD",new BigDecimal("2.000"),4L,OffsetDateTime.parse("2026-08-13T00:00:00Z")); }
}
