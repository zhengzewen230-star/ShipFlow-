package com.shipflow.billing;

import com.shipflow.billing.api.BillingController;
import com.shipflow.billing.api.model.*;
import com.shipflow.billing.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@Import({com.shipflow.security.SecurityConfig.class,com.shipflow.common.exception.GlobalExceptionHandler.class,BillingControllerWebMvcTest.Config.class})
class BillingControllerWebMvcTest {
 @Autowired MockMvc mvc; @MockBean BillingApplicationService service;
 @TestConfiguration static class Config {@Bean JwtDecoder jwtDecoder(){return token->{throw new org.springframework.security.oauth2.jwt.BadJwtException("test");};}}
 @Test void financeCanImportMemoryCsv() throws Exception {when(service.importCsv(eq(7L),eq(2L),eq(3L),any(),eq("bill-key"),isNull())).thenReturn(batch());mvc.perform(multipart("/api/v1/billing/import-batches").file("file","provider_bill_detail_no,tracking_no,billed_amount,currency,fee_type\nD1,T1,10.00,USD,F".getBytes()).param("providerId","3").header("Idempotency-Key","bill-key").with(finance()).with(csrf())).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.id").value(41));}
 @Test void nonFinanceImportIsForbidden() throws Exception {mvc.perform(multipart("/api/v1/billing/import-batches").file("file","x".getBytes()).param("providerId","3").header("Idempotency-Key","bill-key").with(tenantOnly()).with(csrf())).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("COMMON-1004"));verifyNoInteractions(service);}
 @Test void financeListsCurrentTenantBatches() throws Exception {when(service.listBatches(7L,2L,3L,"SUCCESS",1,20)).thenReturn(new BillBatchPageResponse(1,20,1,1,List.of(batch())));mvc.perform(get("/api/v1/billing/import-batches").param("providerId","3").param("status","SUCCESS").with(finance())).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(41));}
 @Test void financeReadsBatchErrorsAndDetails() throws Exception {when(service.listErrors(7L,2L,41L,1,20)).thenReturn(new BillDetailPageResponse(1,20,0,0,List.of()));when(service.listDetails(7L,2L,41L,"ERROR",1,20)).thenReturn(new BillDetailPageResponse(1,20,0,0,List.of()));mvc.perform(get("/api/v1/billing/import-batches/41/errors").with(finance())).andExpect(status().isOk());mvc.perform(get("/api/v1/billing/details").param("batchId","41").param("status","ERROR").with(finance())).andExpect(status().isOk());verify(service).listErrors(7L,2L,41L,1,20);verify(service).listDetails(7L,2L,41L,"ERROR",1,20);}
 @Test void tenantWithoutFinanceReadPermissionCannotReadBilling() throws Exception {mvc.perform(get("/api/v1/billing/import-batches").with(tenantOnly())).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("COMMON-1004"));verifyNoInteractions(service);}
 @Test void crossTenantBatchIsUniform404() throws Exception {when(service.getBatch(7L,2L,41L)).thenThrow(new BillingException("COMMON-1006",404));mvc.perform(get("/api/v1/billing/import-batches/41").with(finance())).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("COMMON-1006"));}
 @Test void billingReadPermissionCanListReconciliationsWithoutConfirmPermission() throws Exception {when(service.listReconciliations(7L,2L,null,null,1,20)).thenReturn(new ReconciliationPageResponse(1,20,0,0,List.of()));mvc.perform(get("/api/v1/reconciliations").with(finance())).andExpect(status().isOk());verify(service).listReconciliations(7L,2L,null,null,1,20);}
 @Test void financeOperationalPermissionCanReadBillingWithoutBillingReadAlias() throws Exception {when(service.listBatches(7L,2L,null,null,1,20)).thenReturn(new BillBatchPageResponse(1,20,0,0,List.of()));mvc.perform(get("/api/v1/billing/import-batches").with(financeOperationalOnly())).andExpect(status().isOk());verify(service).listBatches(7L,2L,null,null,1,20);}
 @Test void reconFinanceCanConfirmWithCsrf() throws Exception {when(service.confirm(eq(7L),eq(2L),eq(81L),any(),isNull())).thenReturn(recon());mvc.perform(post("/api/v1/reconciliations/81/confirm").with(reconcile()).with(csrf()).contentType("application/json").content("{\"resolutionType\":\"ACCEPT\",\"remark\":\"ok\",\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.reconciliationStatus").value("CONFIRMED"));}
 @Test void illegalStatusUsesUnifiedConflict() throws Exception {when(service.confirm(eq(7L),eq(2L),eq(81L),any(),isNull())).thenThrow(new BillingException("RECON-1003",409));mvc.perform(post("/api/v1/reconciliations/81/confirm").with(reconcile()).with(csrf()).contentType("application/json").content("{\"resolutionType\":\"ACCEPT\",\"remark\":\"ok\",\"version\":0}")).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("RECON-1003"));}
 private org.springframework.test.web.servlet.request.RequestPostProcessor finance(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("finance:bill-import"),new SimpleGrantedAuthority("billing:read"));}
 private org.springframework.test.web.servlet.request.RequestPostProcessor financeOperationalOnly(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("finance:bill-import"),new SimpleGrantedAuthority("finance:reconcile"));}
 private org.springframework.test.web.servlet.request.RequestPostProcessor reconcile(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("finance:reconcile"));}
 private org.springframework.test.web.servlet.request.RequestPostProcessor tenantOnly(){return jwt().jwt(j->j.subject("2").claim("tenant_id","7")).authorities(new SimpleGrantedAuthority("scope:TENANT"));}
 private BillBatchResponse batch(){return new BillBatchResponse(41L,3L,"BILL1","bill.csv","a".repeat(64),10,1,1,0,"SUCCESS",OffsetDateTime.now(ZoneOffset.UTC),1L,OffsetDateTime.now(ZoneOffset.UTC),OffsetDateTime.now(ZoneOffset.UTC));}
 private ReconciliationResponse recon(){return new ReconciliationResponse(81L,9L,51L,new java.math.BigDecimal("10.00"),new java.math.BigDecimal("12.00"),new java.math.BigDecimal("2.00"),"CONFIRMED","ACCEPT",2L,OffsetDateTime.now(ZoneOffset.UTC),"ok",1L,OffsetDateTime.now(ZoneOffset.UTC),OffsetDateTime.now(ZoneOffset.UTC));}
}
