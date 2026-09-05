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
    private static final String IMPORT_OPERATION = "importBillingCsv";
    private static final Set<String> BATCH_STATUSES=Set.of("PROCESSING","PARTIAL_SUCCESS","SUCCESS","FAILED");
    private static final Set<String> DETAIL_STATUSES=Set.of("IMPORTED","MATCHED","ERROR");
    private static final Set<String> RECON_STATUSES=Set.of("AUTO_CLOSED","PENDING_CONFIRMATION","CONFIRMED","REJECTED");
    private final BillingMapper mapper; private final ObjectMapper json; private final Clock clock;
    public BillingApplicationService(BillingMapper mapper,ObjectMapper json,Clock clock){this.mapper=mapper;this.json=json;this.clock=clock;}

    @Transactional
    public BillBatchResponse importCsv(Long tenantId,Long operatorId,Long providerId,MultipartFile file,String idempotencyKey,String requestId){
        tenant(tenantId); if(providerId==null||providerId<1||file==null||file.isEmpty())throw new BillingException("BILL-1001",422);
        requireIdempotencyKey(idempotencyKey);
        if(!mapper.providerExists(providerId))throw notFound();
        byte[] bytes; try{bytes=file.getBytes();}catch(Exception e){throw new BillingException("BILL-1001",422);}
        String hash=hash(bytes); String requestHash=hash(providerId+"\n"+hash);
        BillingMapper.IdempotencyRecord idempotency=mapper.findIdempotency(tenantId,IMPORT_OPERATION,idempotencyKey);
        if(idempotency!=null){
            if(!requestHash.equals(idempotency.requestHash()))throw new BillingException("COMMON-1009",409);
            if(!"SUCCEEDED".equals(idempotency.processingStatus())||idempotency.resourceId()==null)throw new BillingException("COMMON-1010",409);
            return batch(requireBatch(tenantId,idempotency.resourceId()));
        }
        try{mapper.insertIdempotency(tenantId,IMPORT_OPERATION,idempotencyKey,"/api/v1/billing/import-batches",requestHash,now().plusMinutes(30));}
        catch(DuplicateKeyException e){
            BillingMapper.IdempotencyRecord same=mapper.findIdempotency(tenantId,IMPORT_OPERATION,idempotencyKey);
            if(same!=null&&requestHash.equals(same.requestHash())&&"SUCCEEDED".equals(same.processingStatus())&&same.resourceId()!=null)return batch(requireBatch(tenantId,same.resourceId()));
            if(same!=null&&requestHash.equals(same.requestHash()))throw new BillingException("COMMON-1010",409);
            throw new BillingException("COMMON-1009",409);
        }
        BillImportBatch prior=mapper.findBatchByHash(tenantId,providerId,hash); if(prior!=null){completeIdempotency(tenantId,idempotencyKey,prior.id());return batch(prior);}
        String fileName=cleanFileName(file.getOriginalFilename()); String batchNo=number("BILL");
        try{mapper.insertBatch(tenantId,providerId,batchNo,fileName,hash,bytes.length,now());}catch(DuplicateKeyException e){prior=mapper.findBatchByHash(tenantId,providerId,hash);if(prior!=null){completeIdempotency(tenantId,idempotencyKey,prior.id());return batch(prior);}throw new BillingException("BILL-1002",409);}
        BillImportBatch created=mapper.findBatchByHash(tenantId,providerId,hash); if(created==null)throw new IllegalStateException("Bill batch was not created");
        ImportSummary summary=importRows(tenantId,providerId,created,bytes,requestId);
        String status=summary.success==0?"FAILED":summary.failure==0&&summary.duplicate==0?"SUCCESS":"PARTIAL_SUCCESS";
        if(mapper.finishBatch(tenantId,created.id(),summary.total,summary.success,summary.failure,summary.duplicate,status,created.version())!=1)throw new BillingException("BILL-1002",409);
        BillImportBatch finished=mapper.findBatch(tenantId,created.id());
        ObjectNode detail=json.createObjectNode().put("providerId",providerId).put("fileHash",hash).put("total",summary.total).put("success",summary.success).put("failure",summary.failure).put("duplicate",summary.duplicate);
        completeIdempotency(tenantId,idempotencyKey,created.id());
        audit(tenantId,operatorId,"BILL_IMPORT","BILL_IMPORT_BATCH",created.id(),requestId,null,detail); return batch(finished);
    }

    public BillBatchPageResponse listBatches(Long tenantId,Long userId,Long providerId,String status,int page,int pageSize){
        tenant(tenantId,userId); page(page,pageSize); String normalized=optional(status,BATCH_STATUSES); if(providerId!=null&&!mapper.providerExists(providerId))throw notFound(); long total=mapper.countBatches(tenantId,userId,providerId,normalized); return new BillBatchPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findBatches(tenantId,userId,providerId,normalized,(page-1)*pageSize,pageSize).stream().map(this::batch).toList());
    }
    public BillBatchResponse getBatch(Long tenantId,Long userId,Long batchId){return batch(requireVisibleBatch(tenantId,userId,batchId));}
    public BillDetailPageResponse listDetails(Long tenantId,Long userId,Long batchId,String status,int page,int pageSize){
        tenant(tenantId,userId); page(page,pageSize); String normalized=optional(status,DETAIL_STATUSES); if(batchId!=null)requireVisibleBatch(tenantId,userId,batchId); long total=mapper.countDetails(tenantId,userId,batchId,normalized); return new BillDetailPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findDetails(tenantId,userId,batchId,normalized,(page-1)*pageSize,pageSize).stream().map(this::detail).toList());
    }
    public BillDetailPageResponse listErrors(Long tenantId,Long userId,Long batchId,int page,int pageSize){return listDetails(tenantId,userId,batchId,"ERROR",page,pageSize);}
    public ReconciliationPageResponse listReconciliations(Long tenantId,Long userId,Long orderId,String status,int page,int pageSize){
        tenant(tenantId,userId); page(page,pageSize); String normalized=optional(status,RECON_STATUSES); long total=mapper.countReconciliations(tenantId,userId,orderId,normalized); return new ReconciliationPageResponse(page,pageSize,pages(total,pageSize),total,mapper.findReconciliations(tenantId,userId,orderId,normalized,(page-1)*pageSize,pageSize).stream().map(this::recon).toList());
    }
    public ReconciliationResponse getReconciliation(Long tenantId,Long userId,Long id){return recon(requireVisibleReconciliation(tenantId,userId,id));}
    @Transactional
    public ReconciliationResponse confirm(Long tenantId,Long operatorId,Long id,ReconciliationConfirmRequest request,String idempotencyKey,String requestId){return act(tenantId,operatorId,id,"CONFIRM",request.resolutionType().trim(),request.remark().trim(),request.version(),idempotencyKey,requestId);}
    @Transactional
    public ReconciliationResponse reject(Long tenantId,Long operatorId,Long id,ReconciliationActionRequest request,String idempotencyKey,String requestId){return act(tenantId,operatorId,id,"REJECT","REJECT",request.remark().trim(),request.version(),idempotencyKey,requestId);}
    @Transactional
    public ReconciliationResponse comment(Long tenantId,Long operatorId,Long id,ReconciliationActionRequest request,String idempotencyKey,String requestId){return act(tenantId,operatorId,id,"COMMENT","COMMENT",request.remark().trim(),request.version(),idempotencyKey,requestId);}
    private ReconciliationResponse act(Long tenantId,Long operatorId,Long id,String action,String resolutionType,String remark,Long version,String idempotencyKey,String requestId){
        requireIdempotencyKey(idempotencyKey); String operation="reconciliation"+action; String requestHash=hash(id+"\n"+action+"\n"+resolutionType+"\n"+remark+"\n"+version);
        BillingMapper.IdempotencyRecord existing=mapper.findIdempotency(tenantId,operation,idempotencyKey);
        if(existing!=null){if(!requestHash.equals(existing.requestHash()))throw new BillingException("COMMON-1009",409);if(!"SUCCEEDED".equals(existing.processingStatus()))throw new BillingException("COMMON-1010",409);return getReconciliation(tenantId,operatorId,id);}
        try{mapper.insertIdempotency(tenantId,operation,idempotencyKey,"/api/v1/reconciliations/"+id+"/"+action.toLowerCase(Locale.ROOT),requestHash,now().plusMinutes(30));}catch(DuplicateKeyException e){throw new BillingException("COMMON-1010",409);}
        ReconciliationRecord current=requireVisibleReconciliation(tenantId,operatorId,id); String before=current.reconciliationStatus(); String after=before; int updated;
        if("CONFIRM".equals(action)){if(!"PENDING_CONFIRMATION".equals(before))throw new BillingException("RECON-1003",409);after="CONFIRMED";updated=mapper.confirmReconciliation(tenantId,id,operatorId,resolutionType,remark,now(),version);}
        else if("REJECT".equals(action)){if(!"PENDING_CONFIRMATION".equals(before))throw new BillingException("RECON-1003",409);after="REJECTED";updated=mapper.rejectReconciliation(tenantId,id,operatorId,remark,now(),version);}
        else{if("AUTO_CLOSED".equals(before))throw new BillingException("RECON-1003",409);updated=mapper.commentReconciliation(tenantId,id,remark,version);}
        if(updated!=1)throw new BillingException("RECON-1003",409);
        mapper.insertReconciliationHistory(tenantId,id,action,before,after,remark,operatorId,requestId,now());
        ObjectNode detail=json.createObjectNode().put("fromStatus",before).put("toStatus",after).put("action",action);audit(tenantId,operatorId,"RECONCILIATION_"+action,"RECONCILIATION_RECORD",id,requestId,remark,detail);
        if(mapper.completeIdempotency(tenantId,operation,idempotencyKey,"RECONCILIATION_RECORD",id,200)!=1)throw new BillingException("COMMON-1010",409);return getReconciliation(tenantId,operatorId,id);
    }
    private ImportSummary importRows(Long tenantId,Long providerId,BillImportBatch batch,byte[] bytes,String requestId){
        String content=new String(bytes,StandardCharsets.UTF_8); String[] rows=content.replace("\r\n","\n").replace('\r','\n').split("\n",-1); if(rows.length<2)throw new BillingException("BILL-1001",422);
        Map<String,Integer> headers=headers(rows[0]); Set<String> seen=new HashSet<>(); int total=0,success=0,failure=0,duplicate=0;
        for(int index=1;index<rows.length;index++){if(rows[index].isBlank())continue;total++; int lineNo=index+1; try{
            String[] values=rows[index].split(",",-1); String no=value(headers,values,"provider_bill_detail_no"); String tracking=value(headers,values,"tracking_no"); String currency=value(headers,values,"currency").toUpperCase(Locale.ROOT); String feeType=value(headers,values,"fee_type"); BigDecimal amount=new BigDecimal(value(headers,values,"billed_amount"));
            if(no.isBlank())throw new BillingException("BILL-1005",422);
            if(!seen.add(no)||mapper.findDetailByProviderNo(tenantId,providerId,no)!=null){insertErrorDetail(tenantId,providerId,batch,lineNo,rows[index],"Duplicate provider bill detail number: "+maskToken(no),"DUP");duplicate++;continue;}
            if(!currency.matches("[A-Z]{3}")||feeType.isBlank()||amount.signum()<0||amount.scale()>2)throw new BillingException("BILL-1004",422);
            OrderFeeMatch order=tracking.isBlank()?null:mapper.findOrderByTracking(tenantId,providerId,tracking);
            if(order==null||!currency.equals(order.currency())){mapper.insertDetail(tenantId,batch.id(),providerId,no,lineNo,null,tracking,amount,currency,feeType,"ERROR",order==null?"Shipment order not found for provider tracking number":"Bill currency does not match order currency",maskRow(rows[index]),"PENDING");failure++;continue;}
            mapper.insertDetail(tenantId,batch.id(),providerId,no,lineNo,order.orderId(),tracking,amount,currency,feeType,"MATCHED",null,null,"NOT_APPLICABLE"); BillDetail detail=mapper.findDetailByBatchLine(tenantId,batch.id(),lineNo); if(detail==null)throw new IllegalStateException("Bill detail was not created"); BigDecimal difference=amount.subtract(order.currentFee()); boolean autoClosed=difference.signum()==0; mapper.insertReconciliation(tenantId,order.orderId(),detail.id(),order.currentFee(),amount,difference,autoClosed?"AUTO_CLOSED":"PENDING_CONFIRMATION"); if(autoClosed){ReconciliationRecord reconciliation=mapper.findReconciliationByDetail(tenantId,detail.id());if(reconciliation==null)throw new IllegalStateException("Reconciliation record was not created");mapper.insertReconciliationHistory(tenantId,reconciliation.id(),"AUTO_CLOSE",null,"AUTO_CLOSED","Zero difference automatically closed",null,requestId,now());} success++;
        }catch(BillingException|NumberFormatException e){insertErrorDetail(tenantId,providerId,batch,lineNo,rows[index],errorReason(e),"ERR");failure++;}}
        return new ImportSummary(total,success,failure,duplicate);
    }
    private Map<String,Integer> headers(String row){String[] fields=row.replace("\uFEFF","").split(",",-1);Map<String,Integer> result=new HashMap<>();for(int i=0;i<fields.length;i++)result.put(fields[i].trim(),i);for(String required:List.of("provider_bill_detail_no","tracking_no","billed_amount","currency","fee_type"))if(!result.containsKey(required))throw new BillingException("BILL-1001",422);return result;}
    private void insertErrorDetail(Long tenantId,Long providerId,BillImportBatch batch,int lineNo,String row,String reason,String prefix){mapper.insertDetail(tenantId,batch.id(),providerId,prefix+"-"+batch.id()+"-"+lineNo,lineNo,null,null,BigDecimal.ZERO,"XXX","INVALID","ERROR",reason,maskRow(row),"PENDING");}
    private String errorReason(Exception error){if(error instanceof BillingException b)return switch(b.code()){case "BILL-1005"->"Provider bill detail number is required";case "BILL-1004"->"Invalid amount, currency, or fee type";default->"Invalid billing row";};return "Invalid billed amount";}
    private String maskToken(String value){if(value==null||value.isBlank())return "***";return value.length()<5?"***":value.substring(0,2)+"***"+value.substring(value.length()-2);}
    private String maskRow(String row){String[] values=row.split(",",-1);for(int i=0;i<values.length;i++){String value=values[i].trim();if(value.length()>4)values[i]=maskToken(value);}String masked=String.join(",",values);return masked.substring(0,Math.min(2000,masked.length()));}
    private String value(Map<String,Integer> headers,String[] values,String name){int i=headers.get(name);return i<values.length?values[i].trim():"";}
    private BillImportBatch requireBatch(Long t,Long id){tenant(t);BillImportBatch value=mapper.findBatch(t,id);if(value==null)throw notFound();return value;}
    private BillImportBatch requireVisibleBatch(Long tenantId,Long userId,Long batchId){tenant(tenantId,userId);BillImportBatch value=mapper.findBatch(tenantId,batchId);if(value==null||!mapper.hasBatchAccess(tenantId,userId,batchId))throw notFound();return value;}
    private ReconciliationRecord requireRecon(Long t,Long id){tenant(t);ReconciliationRecord value=mapper.findReconciliation(t,id);if(value==null)throw notFound();return value;}
    private ReconciliationRecord requireVisibleReconciliation(Long tenantId,Long userId,Long id){tenant(tenantId,userId);ReconciliationRecord value=mapper.findReconciliation(tenantId,id);if(value==null||!mapper.hasReconciliationAccess(tenantId,userId,id))throw notFound();return value;}
    private BillBatchResponse batch(BillImportBatch b){return new BillBatchResponse(b.id(),b.providerId(),b.batchNo(),b.fileName(),b.fileHash(),b.fileSize(),b.totalCount(),b.successCount(),b.failureCount(),b.duplicateCount(),b.status(),offset(b.importedAt()),b.version(),offset(b.createdAt()),offset(b.updatedAt()));}
    private BillDetailResponse detail(BillDetail b){return new BillDetailResponse(b.id(),b.batchId(),b.providerId(),b.providerBillDetailNo(),b.lineNo(),b.shipmentOrderId(),b.trackingNo(),b.billedAmount(),b.currency(),b.feeType(),b.detailStatus(),b.errorMessage(),b.rawLineMasked(),b.errorHandlingStatus(),offset(b.createdAt()),offset(b.updatedAt()));}
    private ReconciliationResponse recon(ReconciliationRecord r){List<ReconciliationActionResponse> history=mapper.findReconciliationHistory(r.tenantId(),r.id()).stream().map(h->new ReconciliationActionResponse(h.id(),h.actionType(),h.statusBefore(),h.statusAfter(),h.remark(),h.operatorUserId(),h.requestId(),offset(h.occurredAt()))).toList();String reason=r.differenceAmount().signum()==0?"NO_DIFFERENCE":"SYSTEM_AND_PROVIDER_AMOUNT_DIFFER";return new ReconciliationResponse(r.id(),r.shipmentOrderId(),r.billDetailId(),r.systemAmount(),r.billedAmount(),r.differenceAmount(),r.reconciliationStatus(),r.resolutionType(),r.confirmedBy(),offset(r.confirmedAt()),r.remark(),r.version(),offset(r.createdAt()),offset(r.updatedAt()),reason,r.confirmedBy(),history);}
    private void audit(Long t,Long u,String action,String type,Long id,String requestId,String reason,ObjectNode detail){try{mapper.insertAudit(t,u,action,type,id,requestId,reason,json.writeValueAsString(detail),now());}catch(Exception e){throw new IllegalStateException("Audit serialization failed",e);}}
    private void completeIdempotency(Long tenantId,String idempotencyKey,Long batchId){if(mapper.completeIdempotency(tenantId,IMPORT_OPERATION,idempotencyKey,"BILL_IMPORT_BATCH",batchId,202)!=1)throw new BillingException("COMMON-1010",409);}
    private void requireIdempotencyKey(String key){if(key==null||key.isBlank()||key.length()>128)throw new BillingException("COMMON-1001",400);}
    private String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
    private String cleanFileName(String name){if(name==null||name.isBlank())return "billing.csv";String v=name.replace('\\','/');v=v.substring(v.lastIndexOf('/')+1);return v.substring(0,Math.min(255,v.length()));}
    private String number(String prefix){return prefix+DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(now())+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
    private String optional(String value,Set<String> allowed){if(value==null||value.isBlank())return null;String v=value.trim().toUpperCase(Locale.ROOT);if(!allowed.contains(v))throw new BillingException("COMMON-1001",400);return v;}
    private void page(int page,int size){if(page<1||size<1||size>100)throw new BillingException("COMMON-1001",400);} private long pages(long total,int size){return (total+size-1)/size;} private void tenant(Long id){if(id==null||id<1)throw new BillingException("COMMON-1004",403);} private void tenant(Long tenantId,Long userId){if(tenantId==null||tenantId<1||userId==null||userId<1)throw new BillingException("COMMON-1004",403);} private BillingException notFound(){return new BillingException("COMMON-1006",404);} private LocalDateTime now(){return LocalDateTime.now(clock);} private OffsetDateTime offset(LocalDateTime v){return v==null?null:v.atOffset(ZoneOffset.UTC);} private record ImportSummary(int total,int success,int failure,int duplicate){}
}
