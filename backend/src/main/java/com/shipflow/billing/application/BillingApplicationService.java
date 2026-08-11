package com.shipflow.billing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shipflow.billing.api.model.*;
import com.shipflow.billing.domain.*;
import com.shipflow.billing.mapper.BillingMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BillingApplicationService {
    private static final Set<String> BATCH_STATUSES=Set.of("PROCESSING","PARTIAL_SUCCESS","SUCCESS","FAILED");
    private static final Set<String> DETAIL_STATUSES=Set.of("IMPORTED","MATCHED","ERROR");
    private static final Set<String> RECON_STATUSES=Set.of("AUTO_CLOSED","PENDING_CONFIRMATION","CONFIRMED","REJECTED");
    private final BillingMapper mapper; private final ObjectMapper json; private final Clock clock;
    public BillingApplicationService(BillingMapper mapper,ObjectMapper json,Clock clock){this.mapper=mapper;this.json=json;this.clock=clock;}

    @Transactional
    public BillBatchResponse importCsv(Long tenantId,Long operatorId,Long providerId,MultipartFile file,String requestId){
        tenant(tenantId); if(providerId==null||providerId<1||file==null||file.isEmpty())throw new BillingException("BILL-1001",422);
        if(!mapper.providerExists(providerId))throw notFound();
        byte[] bytes; try{bytes=file.getBytes();}catch(Exception e){throw new BillingException("BILL-1001",422);}
        String hash=hash(bytes); BillImportBatch prior=mapper.findBatchByHash(tenantId,providerId,hash); if(prior!=null)return batch(prior);
        String fileName=cleanFileName(file.getOriginalFilename()); String batchNo=number("BILL");
        try{mapper.insertBatch(tenantId,providerId,batchNo,fileName,hash,bytes.length,now());}catch(DuplicateKeyException e){prior=mapper.findBatchByHash(tenantId,providerId,hash);if(prior!=null)return batch(prior);throw new BillingException("BILL-1002",409);}
        BillImportBatch created=mapper.findBatchByHash(tenantId,providerId,hash); if(created==null)throw new IllegalStateException("Bill batch was not created");
        ImportSummary summary=importRows(tenantId,providerId,created,bytes);
        String status=summary.success==0?"FAILED":summary.failure==0?"SUCCESS":"PARTIAL_SUCCESS";
        if(mapper.finishBatch(tenantId,created.id(),summary.total,summary.success,summary.failure,status,created.version())!=1)throw new BillingException("BILL-1002",409);
        BillImportBatch finished=mapper.findBatch(tenantId,created.id());
        ObjectNode detail=json.createObjectNode().put("providerId",providerId).put("fileHash",hash).put("total",summary.total).put("success",summary.success).put("failure",summary.failure);
        audit(tenantId,operatorId,"BILL_IMPORT","BILL_IMPORT_BATCH",created.id(),requestId,null,detail); return batch(finished);
    }

    public BillBatchPageResponse listBatches(Long tenantId,Long providerId,String status,int page,int pageSize){
        tenant(tenantId); page(page,pageSize); String normalized=optional(status,BATCH_STATUSES); if(providerId!=null&&!mapper.providerExists(providerId))throw notFound(); long total=mapper.countBatches(tenantId,providerId,normalized); return new BillBatchPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findBatches(tenantId,providerId,normalized,(page-1)*pageSize,pageSize).stream().map(this::batch).toList());
    }
    public BillBatchResponse getBatch(Long tenantId,Long batchId){return batch(requireBatch(tenantId,batchId));}
    public BillDetailPageResponse listDetails(Long tenantId,Long batchId,String status,int page,int pageSize){
        tenant(tenantId); page(page,pageSize); String normalized=optional(status,DETAIL_STATUSES); if(batchId!=null)requireBatch(tenantId,batchId); long total=mapper.countDetails(tenantId,batchId,normalized); return new BillDetailPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findDetails(tenantId,batchId,normalized,(page-1)*pageSize,pageSize).stream().map(this::detail).toList());
    }
    public BillDetailPageResponse listErrors(Long tenantId,Long batchId,int page,int pageSize){return listDetails(tenantId,batchId,"ERROR",page,pageSize);}
    public ReconciliationPageResponse listReconciliations(Long tenantId,Long orderId,String status,int page,int pageSize){
        tenant(tenantId); page(page,pageSize); String normalized=optional(status,RECON_STATUSES); long total=mapper.countReconciliations(tenantId,orderId,normalized); return new ReconciliationPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findReconciliations(tenantId,orderId,normalized,(page-1)*pageSize,pageSize).stream().map(this::recon).toList());
    }
    public ReconciliationResponse getReconciliation(Long tenantId,Long id){return recon(requireRecon(tenantId,id));}
    @Transactional
    public ReconciliationResponse confirm(Long tenantId,Long operatorId,Long id,ReconciliationConfirmRequest request,String requestId){
        ReconciliationRecord current=requireRecon(tenantId,id); if(!"PENDING_CONFIRMATION".equals(current.reconciliationStatus()))throw new BillingException("RECON-1003",409);
        if(mapper.confirmReconciliation(tenantId,id,operatorId,request.resolutionType().trim(),request.remark().trim(),now(),request.version())!=1)throw new BillingException("RECON-1003",409);
        ObjectNode detail=json.createObjectNode().put("fromStatus","PENDING_CONFIRMATION").put("toStatus","CONFIRMED").put("resolutionType",request.resolutionType().trim()); audit(tenantId,operatorId,"RECONCILIATION_CONFIRM","RECONCILIATION_RECORD",id,requestId,request.remark().trim(),detail); return getReconciliation(tenantId,id);
    }
    private ImportSummary importRows(Long tenantId,Long providerId,BillImportBatch batch,byte[] bytes){
        String content=new String(bytes,StandardCharsets.UTF_8); String[] rows=content.replace("\r\n","\n").replace('\r','\n').split("\n",-1); if(rows.length<2)throw new BillingException("BILL-1001",422);
        Map<String,Integer> headers=headers(rows[0]); Set<String> seen=new HashSet<>(); int total=0,success=0,failure=0;
        for(int index=1;index<rows.length;index++){if(rows[index].isBlank())continue;total++; int lineNo=index+1; try{
            String[] values=rows[index].split(",",-1); String no=value(headers,values,"provider_bill_detail_no"); String tracking=value(headers,values,"tracking_no"); String currency=value(headers,values,"currency").toUpperCase(Locale.ROOT); String feeType=value(headers,values,"fee_type"); BigDecimal amount=new BigDecimal(value(headers,values,"billed_amount"));
            if(no.isBlank())throw new BillingException("BILL-1005",422); if(!seen.add(no)||mapper.findDetailByProviderNo(tenantId,providerId,no)!=null)throw new BillingException("BILL-1003",409); if(!currency.matches("[A-Z]{3}")||feeType.isBlank()||amount.signum()<0||amount.scale()>2)throw new BillingException("BILL-1004",422);
            OrderFeeMatch order=tracking.isBlank()?null:mapper.findOrderByTracking(tenantId,providerId,tracking);
            if(order==null||!currency.equals(order.currency())){mapper.insertDetail(tenantId,batch.id(),providerId,no,lineNo,null,tracking,amount,currency,feeType,"ERROR",order==null?"Shipment order not found for provider tracking number":"Bill currency does not match order currency");failure++;continue;}
            mapper.insertDetail(tenantId,batch.id(),providerId,no,lineNo,order.orderId(),tracking,amount,currency,feeType,"MATCHED",null); BillDetail detail=mapper.findDetailByBatchLine(tenantId,batch.id(),lineNo); if(detail==null)throw new IllegalStateException("Bill detail was not created"); BigDecimal difference=amount.subtract(order.currentFee()); mapper.insertReconciliation(tenantId,order.orderId(),detail.id(),order.currentFee(),amount,difference,difference.signum()==0?"AUTO_CLOSED":"PENDING_CONFIRMATION"); success++;
        }catch(BillingException|NumberFormatException e){failure++;}}
        return new ImportSummary(total,success,failure);
    }
    private Map<String,Integer> headers(String row){String[] fields=row.replace("\uFEFF","").split(",",-1);Map<String,Integer> result=new HashMap<>();for(int i=0;i<fields.length;i++)result.put(fields[i].trim(),i);for(String required:List.of("provider_bill_detail_no","tracking_no","billed_amount","currency","fee_type"))if(!result.containsKey(required))throw new BillingException("BILL-1001",422);return result;}
    private String value(Map<String,Integer> headers,String[] values,String name){int i=headers.get(name);return i<values.length?values[i].trim():"";}
    private BillImportBatch requireBatch(Long t,Long id){tenant(t);BillImportBatch value=mapper.findBatch(t,id);if(value==null)throw notFound();return value;}
    private ReconciliationRecord requireRecon(Long t,Long id){tenant(t);ReconciliationRecord value=mapper.findReconciliation(t,id);if(value==null)throw notFound();return value;}
    private BillBatchResponse batch(BillImportBatch b){return new BillBatchResponse(b.id(),b.providerId(),b.batchNo(),b.fileName(),b.fileHash(),b.fileSize(),b.totalCount(),b.successCount(),b.failureCount(),b.status(),offset(b.importedAt()),b.version(),offset(b.createdAt()),offset(b.updatedAt()));}
    private BillDetailResponse detail(BillDetail b){return new BillDetailResponse(b.id(),b.batchId(),b.providerId(),b.providerBillDetailNo(),b.lineNo(),b.shipmentOrderId(),b.trackingNo(),b.billedAmount(),b.currency(),b.feeType(),b.detailStatus(),b.errorMessage(),offset(b.createdAt()),offset(b.updatedAt()));}
    private ReconciliationResponse recon(ReconciliationRecord r){return new ReconciliationResponse(r.id(),r.shipmentOrderId(),r.billDetailId(),r.systemAmount(),r.billedAmount(),r.differenceAmount(),r.reconciliationStatus(),r.resolutionType(),r.confirmedBy(),offset(r.confirmedAt()),r.remark(),r.version(),offset(r.createdAt()),offset(r.updatedAt()));}
    private void audit(Long t,Long u,String action,String type,Long id,String requestId,String reason,ObjectNode detail){try{mapper.insertAudit(t,u,action,type,id,requestId,reason,json.writeValueAsString(detail),now());}catch(Exception e){throw new IllegalStateException("Audit serialization failed",e);}}
    private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    private String cleanFileName(String name){if(name==null||name.isBlank())return "billing.csv";String v=name.replace('\\','/');v=v.substring(v.lastIndexOf('/')+1);return v.substring(0,Math.min(255,v.length()));}
    private String number(String prefix){return prefix+DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now())+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
    private String optional(String value,Set<String> allowed){if(value==null||value.isBlank())return null;String v=value.trim().toUpperCase(Locale.ROOT);if(!allowed.contains(v))throw new BillingException("COMMON-1001",400);return v;}
    private void page(int page,int size){if(page<1||size<1||size>100)throw new BillingException("COMMON-1001",400);} private long pages(long total,int size){return (total+size-1)/size;} private void tenant(Long id){if(id==null||id<1)throw new BillingException("COMMON-1004",403);} private BillingException notFound(){return new BillingException("COMMON-1006",404);} private LocalDateTime now(){return LocalDateTime.now(clock);} private OffsetDateTime offset(LocalDateTime v){return v==null?null:v.atOffset(ZoneOffset.UTC);} private record ImportSummary(int total,int success,int failure){}
}
