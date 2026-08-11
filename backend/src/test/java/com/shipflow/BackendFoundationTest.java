package com.shipflow;

import com.shipflow.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.shipflow.store.mapper.StoreMapper;
import com.shipflow.store.mapper.StoreIdempotencyMapper;
import com.shipflow.store.mapper.StoreAuditMapper;
import com.shipflow.user.mapper.UserMapper;
import com.shipflow.user.mapper.UserIdempotencyMapper;
import com.shipflow.user.mapper.UserAuditMapper;
import com.shipflow.rbac.mapper.RoleMapper;
import com.shipflow.rbac.mapper.PermissionMapper;
import com.shipflow.rbac.mapper.RbacAuditMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.logistics.mapper.LogisticsMasterMapper;
import com.shipflow.logistics.mapper.LogisticsIdempotencyMapper;
import com.shipflow.logistics.mapper.LogisticsAuditMapper;
import com.shipflow.quote.mapper.QuoteMapper;
import com.shipflow.quote.mapper.QuoteIdempotencyMapper;
import com.shipflow.quote.mapper.QuotePricingMapper;
import com.shipflow.quote.mapper.QuoteAuditMapper;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.order.mapper.ShipmentOrderIdempotencyMapper;
import com.shipflow.warehouse.mapper.WarehouseMapper;
import com.shipflow.tracking.mapper.TrackingQueryMapper;
import com.shipflow.tracking.mapper.TrackingCallbackMapper;
import com.shipflow.exceptioncase.mapper.ExceptionClaimMapper;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendFoundationTest {

    @MockBean StoreMapper storeMapper;
    @MockBean StoreIdempotencyMapper storeIdempotencyMapper;
    @MockBean StoreAuditMapper storeAuditMapper;
    @MockBean UserMapper userMapper;
    @MockBean UserIdempotencyMapper userIdempotencyMapper;
    @MockBean UserAuditMapper userAuditMapper;
    @MockBean RoleMapper roleMapper;
    @MockBean PermissionMapper permissionMapper;
    @MockBean RbacAuditMapper rbacAuditMapper;
    @MockBean TenantMapper tenantMapper;
    @MockBean TenantProvisioningMapper tenantProvisioningMapper;
    @MockBean TenantIdempotencyMapper tenantIdempotencyMapper;
    @MockBean TenantAuditMapper tenantAuditMapper;
    @MockBean LogisticsMasterMapper logisticsMasterMapper;
    @MockBean LogisticsIdempotencyMapper logisticsIdempotencyMapper;
    @MockBean LogisticsAuditMapper logisticsAuditMapper;
    @MockBean QuoteMapper quoteMapper;
    @MockBean QuoteIdempotencyMapper quoteIdempotencyMapper;
    @MockBean QuotePricingMapper quotePricingMapper;
    @MockBean QuoteAuditMapper quoteAuditMapper;
    @MockBean ShipmentOrderMapper shipmentOrderMapper;
    @MockBean ShipmentOrderIdempotencyMapper shipmentOrderIdempotencyMapper;
    @MockBean WarehouseMapper warehouseMapper;
    @MockBean TrackingQueryMapper trackingQueryMapper;
    @MockBean TrackingCallbackMapper trackingCallbackMapper;
    @MockBean ExceptionClaimMapper exceptionClaimMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unifiedSuccessResponseContainsTraceId() throws Exception {
        mockMvc.perform(get("/_test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void traceIdIsPropagated() throws Exception {
        mockMvc.perform(get("/_test/ok").header("X-Trace-Id", "test-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "test-trace-id"))
                .andExpect(jsonPath("$.traceId").value("test-trace-id"));
    }

    @Test
    void globalExceptionIsMappedToCommonError() throws Exception {
        mockMvc.perform(get("/_test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-1007"));
    }

    @Test
    void validationExceptionIsMappedToBadRequest() throws Exception {
        mockMvc.perform(post("/_test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.error.code").value("COMMON-1001"))
                .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void responseRecordHasExpectedSuccessShape() {
        ApiResponse<String> response = ApiResponse.success("value");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("value");
    }
}
