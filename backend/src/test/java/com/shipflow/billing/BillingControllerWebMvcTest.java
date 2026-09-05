package com.shipflow.billing;

import com.shipflow.billing.api.BillingController;
import com.shipflow.billing.api.model.*;
import com.shipflow.billing.application.BillingApplicationService;
import com.shipflow.billing.application.BillingException;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@Import({com.shipflow.security.SecurityConfig.class,com.shipflow.common.exception.GlobalExceptionHandler.class,BillingControllerWebMvcTest.Config.class})
class BillingControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean BillingApplicationService service;
    @TestConfiguration static class Config {@Bean JwtDecoder jwtDecoder(){return token->{throw new org.springframework.security.oauth2.jwt.BadJwtException("test");};}}

    @Test void importAndQueryExposeV023Fields() throws Exception {
        when(service.importCsv(eq(7L),eq(2L),eq(3L),any(),eq("bill-key"),isNull())).thenReturn(batch());
        mvc.perform(multipart("/api/v1/billing/import-batches").file("file","x".getBytes()).param("providerId","3").header("Idempotency-Key","bill-key").with(finance()).with(csrf()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.duplicateCount").value(1));
        when(service.listErrors(7L,2L,41L,1,20)).thenReturn(new BillDetailPageResponse(1,20,1,1,List.of(errorDetail())));
        mvc.perform(get("/api/v1/billing/import-batches/41/errors").with(finance()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].rawLineMasked").exists()).andExpect(jsonPath("$.data.items[0].errorHandlingStatus").value("PENDING"));
    }

    @Test void reconciliationWritesRequirePermissionCsrfAndIdempotency() throws Exception {
        when(service.confirm(eq(7L),eq(2L),eq(81L),any(),eq("confirm-key"),eq("req-1"))).thenReturn(recon("CONFIRMED"));
        mvc.perform(post("/api/v1/reconciliations/81/confirm").header("Idempotency-Key","confirm-key").header("X-Request-Id","req-1").with(reconcile()).with(csrf()).contentType("application/json").content("{\"resolutionType\":\"ACCEPT\",\"remark\":\"ok\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reconciliationStatus").value("CONFIRMED"));
        when(service.reject(eq(7L),eq(2L),eq(81L),any(),eq("reject-key"),isNull())).thenReturn(recon("REJECTED"));
        mvc.perform(post("/api/v1/reconciliations/81/reject").header("Idempotency-Key","reject-key").with(reconcile()).with(csrf()).contentType("application/json").content("{\"remark\":\"reject reason\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reconciliationStatus").value("REJECTED"));
        when(service.comment(eq(7L),eq(2L),eq(81L),any(),eq("comment-key"),isNull())).thenReturn(recon("PENDING_CONFIRMATION"));
        mvc.perform(post("/api/v1/reconciliations/81/comments").header("Idempotency-Key","comment-key").with(reconcile()).with(csrf()).contentType("application/json").content("{\"remark\":\"more context\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    @Test void missingIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/reconciliations/81/confirm").with(reconcile()).with(csrf()).contentType("application/json").content("{\"resolutionType\":\"ACCEPT\",\"remark\":\"ok\",\"version\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test void permissionAndTenantBoundariesRemainEnforced() throws Exception {
        mvc.perform(post("/api/v1/reconciliations/81/reject").header("Idempotency-Key","k").with(tenantOnly()).with(csrf()).contentType("application/json").content("{\"remark\":\"no\",\"version\":0}"))
                .andExpect(status().isForbidden());
        when(service.getBatch(7L,2L,41L)).thenThrow(new BillingException("COMMON-1006",404));
        mvc.perform(get("/api/v1/billing/import-batches/41").with(finance())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COMMON-1006"));
    }

    @Test void versionConflictReturnsExplicitReasonAndTraceId() throws Exception {
        when(service.comment(eq(7L),eq(2L),eq(82L),any(),eq("stale-key"),eq("req-stale")))
                .thenThrow(new BillingException("RECON-1003",409));
        mvc.perform(post("/api/v1/reconciliations/82/comments")
                        .header("Idempotency-Key","stale-key")
                        .header("X-Request-Id","req-stale")
                        .with(reconcile()).with(csrf())
                        .contentType("application/json")
                        .content("{\"remark\":\"stale version\",\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RECON-1003"))
                .andExpect(jsonPath("$.error.message").value("对账状态或版本冲突，请刷新后重试"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor finance(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("finance:bill-import"),new SimpleGrantedAuthority("billing:read"));}
    private org.springframework.test.web.servlet.request.RequestPostProcessor reconcile(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("finance:reconcile"));}
    private org.springframework.test.web.servlet.request.RequestPostProcessor tenantOnly(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"));}
    private BillBatchResponse batch(){var now=OffsetDateTime.now(ZoneOffset.UTC);return new BillBatchResponse(41L,3L,"BILL1","bill.csv","a".repeat(64),10,3,1,1,1,"PARTIAL_SUCCESS",now,1L,now,now);}
    private BillDetailResponse errorDetail(){var now=OffsetDateTime.now(ZoneOffset.UTC);return new BillDetailResponse(51L,41L,3L,"ERR-41-2",2,null,null,BigDecimal.ZERO,"XXX","INVALID","ERROR","Invalid billed amount","D1,***","PENDING",now,now);}
    private ReconciliationResponse recon(String status){var now=OffsetDateTime.now(ZoneOffset.UTC);return new ReconciliationResponse(81L,9L,51L,new BigDecimal("10.00"),new BigDecimal("12.00"),new BigDecimal("2.00"),status,"ACCEPT",2L,now,"ok",1L,now,now,"SYSTEM_AND_PROVIDER_AMOUNT_DIFFER",2L,List.of());}
}
