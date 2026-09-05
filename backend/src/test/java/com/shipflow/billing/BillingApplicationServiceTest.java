package com.shipflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipflow.billing.api.model.ReconciliationActionRequest;
import com.shipflow.billing.api.model.ReconciliationConfirmRequest;
import com.shipflow.billing.application.BillingApplicationService;
import com.shipflow.billing.application.BillingException;
import com.shipflow.billing.domain.BillDetail;
import com.shipflow.billing.domain.BillImportBatch;
import com.shipflow.billing.domain.OrderFeeMatch;
import com.shipflow.billing.domain.ReconciliationRecord;
import com.shipflow.billing.mapper.BillingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillingApplicationServiceTest {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,8,11,12,0);
    private BillingMapper mapper;
    private BillingApplicationService service;

    @BeforeEach void setUp(){
        mapper=mock(BillingMapper.class);
        when(mapper.finishBatch(anyLong(),anyLong(),anyInt(),anyInt(),anyInt(),anyInt(),anyString(),anyLong())).thenReturn(1);
        when(mapper.completeIdempotency(anyLong(),anyString(),anyString(),anyString(),anyLong(),anyInt())).thenReturn(1);
        when(mapper.findReconciliationHistory(anyLong(),anyLong())).thenReturn(List.of());
        service=new BillingApplicationService(mapper,new ObjectMapper(),Clock.fixed(NOW.toInstant(ZoneOffset.UTC),ZoneOffset.UTC));
    }

    @Test void matchedRowUsesRealAmountsAndCreatesManualReconciliation(){
        prepareImport(batch(41L,"SUCCESS",1,1,0,0,1L));
        when(mapper.findOrderByTracking(7L,3L,"TN1")).thenReturn(new OrderFeeMatch(9L,new BigDecimal("10.00"),"USD"));
        when(mapper.findDetailByBatchLine(7L,41L,2)).thenReturn(detail(51L,"MATCHED"));
        var result=service.importCsv(7L,2L,3L,file("D1,TN1,12.00,USD,FREIGHT"),"bill-key","req-1");
        assertThat(result.duplicateCount()).isZero();
        verify(mapper).insertDetail(7L,41L,3L,"D1",2,9L,"TN1",new BigDecimal("12.00"),"USD","FREIGHT","MATCHED",null,null,"NOT_APPLICABLE");
        verify(mapper).insertReconciliation(7L,9L,51L,new BigDecimal("10.00"),new BigDecimal("12.00"),new BigDecimal("2.00"),"PENDING_CONFIRMATION");
        verify(mapper).finishBatch(7L,41L,1,1,0,0,"SUCCESS",0L);
    }

    @Test void exactAmountIsAutoClosedOnlyByBackend(){
        prepareImport(batch(41L,"SUCCESS",1,1,0,0,1L));
        when(mapper.findOrderByTracking(7L,3L,"TN1")).thenReturn(new OrderFeeMatch(9L,new BigDecimal("10.00"),"USD"));
        when(mapper.findDetailByBatchLine(7L,41L,2)).thenReturn(detail(51L,"MATCHED"));
        when(mapper.findReconciliationByDetail(7L,51L)).thenReturn(recon("AUTO_CLOSED",0L));
        service.importCsv(7L,2L,3L,file("D1,TN1,10.00,USD,FREIGHT"),"bill-key","req-auto");
        verify(mapper).insertReconciliation(7L,9L,51L,new BigDecimal("10.00"),new BigDecimal("10.00"),new BigDecimal("0.00"),"AUTO_CLOSED");
        verify(mapper).insertReconciliationHistory(7L,81L,"AUTO_CLOSE",null,"AUTO_CLOSED","Zero difference automatically closed",null,"req-auto",NOW);
    }

    @Test void duplicateRowIsPersistedAndCountedSeparately(){
        prepareImport(batch(41L,"PARTIAL_SUCCESS",2,1,0,1,1L));
        when(mapper.findOrderByTracking(7L,3L,"TN1")).thenReturn(new OrderFeeMatch(9L,new BigDecimal("10.00"),"USD"));
        when(mapper.findDetailByBatchLine(7L,41L,2)).thenReturn(detail(51L,"MATCHED"));
        when(mapper.findReconciliationByDetail(7L,51L)).thenReturn(recon("AUTO_CLOSED",0L));
        var result=service.importCsv(7L,2L,3L,file("D1,TN1,10.00,USD,FREIGHT\nD1,TN1,10.00,USD,FREIGHT"),"bill-key",null);
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(mapper).insertDetail(eq(7L),eq(41L),eq(3L),eq("DUP-41-3"),eq(3),isNull(),isNull(),eq(BigDecimal.ZERO),eq("XXX"),eq("INVALID"),eq("ERROR"),contains("Duplicate"),anyString(),eq("PENDING"));
        verify(mapper).finishBatch(7L,41L,2,1,0,1,"PARTIAL_SUCCESS",0L);
    }

    @Test void malformedAndUnmatchedRowsArePersistedAsPendingErrors(){
        prepareImport(batch(41L,"FAILED",2,0,2,0,1L));
        service.importCsv(7L,2L,3L,file("D1,TN1,not-money,USD,FREIGHT\nD2,MISS,10.00,USD,FREIGHT"),"bill-key",null);
        verify(mapper).insertDetail(eq(7L),eq(41L),eq(3L),eq("ERR-41-2"),eq(2),isNull(),isNull(),eq(BigDecimal.ZERO),eq("XXX"),eq("INVALID"),eq("ERROR"),contains("amount"),anyString(),eq("PENDING"));
        verify(mapper).insertDetail(eq(7L),eq(41L),eq(3L),eq("D2"),eq(3),isNull(),eq("MISS"),eq(new BigDecimal("10.00")),eq("USD"),eq("FREIGHT"),eq("ERROR"),contains("not found"),anyString(),eq("PENDING"));
        verify(mapper).finishBatch(7L,41L,2,0,2,0,"FAILED",0L);
    }

    @Test void confirmationRejectAndCommentRequireIdempotencyAndVersion(){
        when(mapper.hasReconciliationAccess(7L,2L,81L)).thenReturn(true);
        when(mapper.findReconciliation(7L,81L)).thenReturn(recon("PENDING_CONFIRMATION",0L),recon("CONFIRMED",1L));
        when(mapper.confirmReconciliation(7L,81L,2L,"ACCEPT","ok",NOW,0L)).thenReturn(1);
        var confirmed=service.confirm(7L,2L,81L,new ReconciliationConfirmRequest("ACCEPT","ok",0L),"confirm-key","req-1");
        assertThat(confirmed.reconciliationStatus()).isEqualTo("CONFIRMED");
        verify(mapper).insertReconciliationHistory(7L,81L,"CONFIRM","PENDING_CONFIRMATION","CONFIRMED","ok",2L,"req-1",NOW);
        verify(mapper).insertAudit(eq(7L),eq(2L),eq("RECONCILIATION_CONFIRM"),eq("RECONCILIATION_RECORD"),eq(81L),eq("req-1"),eq("ok"),anyString(),eq(NOW));
    }

    @Test void autoClosedCannotBeManuallyChangedAndIdempotencyConflictIsExplicit(){
        when(mapper.hasReconciliationAccess(7L,2L,81L)).thenReturn(true);
        when(mapper.findReconciliation(7L,81L)).thenReturn(recon("AUTO_CLOSED",0L));
        assertCode(()->service.reject(7L,2L,81L,new ReconciliationActionRequest("no",0L),"reject-key",null),"RECON-1003");
        when(mapper.findIdempotency(7L,"reconciliationCOMMENT","same-key")).thenReturn(new BillingMapper.IdempotencyRecord("different",81L,"SUCCEEDED"));
        assertCode(()->service.comment(7L,2L,81L,new ReconciliationActionRequest("note",0L),"same-key",null),"COMMON-1009");
    }

    private void prepareImport(BillImportBatch finished){
        when(mapper.providerExists(3L)).thenReturn(true);
        when(mapper.findBatchByHash(eq(7L),eq(3L),anyString())).thenReturn(null,batch(41L,"PROCESSING",0,0,0,0,0L));
        when(mapper.findBatch(7L,41L)).thenReturn(finished);
    }
    private MockMultipartFile file(String rows){return new MockMultipartFile("file","bill.csv","text/csv",("provider_bill_detail_no,tracking_no,billed_amount,currency,fee_type\n"+rows).getBytes(StandardCharsets.UTF_8));}
    private BillImportBatch batch(Long id,String status,int total,int success,int failure,int duplicate,Long version){return new BillImportBatch(id,7L,3L,"BILL1","bill.csv","a".repeat(64),20,total,success,failure,duplicate,status,NOW,version,NOW,NOW);}
    private BillDetail detail(Long id,String status){return new BillDetail(id,7L,41L,3L,"D1",2,9L,"TN1",new BigDecimal("10.00"),"USD","FREIGHT",status,null,null,"NOT_APPLICABLE",NOW,NOW);}
    private ReconciliationRecord recon(String status,Long version){return new ReconciliationRecord(81L,7L,9L,51L,new BigDecimal("10.00"),new BigDecimal("12.00"),new BigDecimal("2.00"),status,null,null,null,null,version,NOW,NOW);}
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,String code){assertThatThrownBy(call).isInstanceOf(BillingException.class).satisfies(e->assertThat(((BillingException)e).code()).isEqualTo(code));}
}
