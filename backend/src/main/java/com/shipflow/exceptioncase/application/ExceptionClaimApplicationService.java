package com.shipflow.exceptioncase.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shipflow.exceptioncase.api.model.*;
import com.shipflow.exceptioncase.domain.*;
import com.shipflow.exceptioncase.mapper.ExceptionClaimMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExceptionClaimApplicationService {
    private static final Set<String> EXCEPTION_TYPES=Set.of("ADDRESS","CUSTOMS","TRANSPORT","OTHER");
    private static final Set<String> EXCEPTION_STATUSES=Set.of("OPEN","PROCESSING","RESOLVED","CLOSED");
    private static final String CREATE_EXCEPTION="createExceptionCase", CREATE_CLAIM="createClaimRecord";
    private final ExceptionClaimMapper mapper; private final ObjectMapper json; private final Clock clock;

    public ExceptionClaimApplicationService(ExceptionClaimMapper mapper,ObjectMapper json,Clock clock){this.mapper=mapper;this.json=json;this.clock=clock;}

    public ExceptionCasePageResponse list(Long tenantId,Long orderId,String status,int page,int pageSize){
        tenant(tenantId); if(page<1||pageSize<1||pageSize>100)throw badRequest();
        String normalized=optionalStatus(status,EXCEPTION_STATUSES);
        if(orderId!=null&&!mapper.orderExists(tenantId,orderId))throw notFound();
        long total=mapper.countExceptions(tenantId,orderId,normalized);
        var items=mapper.findExceptions(tenantId,orderId,normalized,(page-1)*pageSize,pageSize).stream().map(e->response(e,null)).toList();
        return new ExceptionCasePageResponse(page,pageSize,(total+pageSize-1)/pageSize,total,items);
    }

    public ExceptionCaseResponse get(Long tenantId,Long exceptionId){
        ExceptionCase e=requireException(tenantId,exceptionId); return response(e,mapper.findClaimByException(tenantId,exceptionId));
    }

    public ClaimResponse getClaim(Long tenantId,Long claimId){return claimResponse(requireClaim(tenantId,claimId));}

    @Transactional
    public ExceptionCaseResponse createException(Long tenantId,Long operatorUserId,Long orderId,
                                                 CreateExceptionRequest request,String key,String requestId){
        tenant(tenantId); String type=upper(request.exceptionType()); if(!EXCEPTION_TYPES.contains(type))throw new ExceptionClaimException("EXCEPTION-1001",422);
        String hash=sha256(orderId+"\n"+type+"\n"+request.description()+"\n"+request.reportedAt().toInstant()+"\n"+request.trackingEventId());
        var prior=claimIdempotency(tenantId,CREATE_EXCEPTION,key,hash,"/api/v1/orders/{orderId}/exceptions");
        if(prior!=null)return get(tenantId,prior.resourceId());
        if(!mapper.orderExists(tenantId,orderId))throw notFound();
        if(request.trackingEventId()!=null&&!mapper.trackingEventExists(tenantId,orderId,request.trackingEventId()))throw notFound();
        String no=number("EX",now()); mapper.insertException(tenantId,orderId,no,type,request.description(),utc(request.reportedAt()));
        ExceptionCase created=mapper.findExceptionByNo(tenantId,no); if(created==null)throw new IllegalStateException("Exception case was not created");
        ObjectNode detail=json.createObjectNode().put("orderId",orderId).put("exceptionType",type);
        if(request.trackingEventId()!=null)detail.put("trackingEventId",request.trackingEventId());
        audit(tenantId,operatorUserId,"EXCEPTION_CREATE","EXCEPTION_CASE",created.id(),requestId,null,detail);
        mapper.completeIdempotency(tenantId,CREATE_EXCEPTION,key,"EXCEPTION_CASE",created.id()); return response(created,null);
    }

    @Transactional
    public ExceptionCaseResponse assign(Long tenantId,Long operatorUserId,Long exceptionId,
                                        AssignExceptionRequest request,String requestId){
        ExceptionCase current=requireException(tenantId,exceptionId);
        if(!mapper.activeTenantUserExists(tenantId,request.assignedToUserId()))throw notFound();
        if(!ExceptionStateMachine.canAssign(current.status())||mapper.transitionException(tenantId,exceptionId,"OPEN","PROCESSING",request.version())!=1)throw new ExceptionClaimException("EXCEPTION-1002",409);
        ObjectNode detail=json.createObjectNode().put("assignedToUserId",request.assignedToUserId()).put("fromStatus","OPEN").put("toStatus","PROCESSING");
        audit(tenantId,operatorUserId,"EXCEPTION_ASSIGN","EXCEPTION_CASE",exceptionId,requestId,request.reason(),detail);
        return get(tenantId,exceptionId);
    }

    @Transactional
    public ExceptionCaseResponse transitionException(Long tenantId,Long operatorUserId,Long exceptionId,
                                                     ExceptionStatusRequest request,String requestId){
        ExceptionCase current=requireException(tenantId,exceptionId); String target=upper(request.status());
        if(!ExceptionStateMachine.canTransition(current.status(),target)
                ||mapper.transitionException(tenantId,exceptionId,current.status(),target,request.version())!=1)throw new ExceptionClaimException("EXCEPTION-1002",409);
        ObjectNode detail=json.createObjectNode().put("fromStatus",current.status()).put("toStatus",target);
        audit(tenantId,operatorUserId,"EXCEPTION_"+target,"EXCEPTION_CASE",exceptionId,requestId,request.reason(),detail);
        return get(tenantId,exceptionId);
    }

    @Transactional
    public ClaimResponse createClaim(Long tenantId,Long operatorUserId,Long exceptionId,
                                     CreateClaimRequest request,String key,String requestId){
        tenant(tenantId); String currency=upper(request.currency());
        if(request.claimAmount()==null||request.claimAmount().signum()<=0||request.claimAmount().scale()>2)throw new ExceptionClaimException("CLAIM-1003",422);
        String hash=sha256(exceptionId+"\n"+request.claimAmount().stripTrailingZeros().toPlainString()+"\n"+currency);
        var prior=claimIdempotency(tenantId,CREATE_CLAIM,key,hash,"/api/v1/exceptions/{exceptionId}/claim");
        if(prior!=null)return getClaim(tenantId,prior.resourceId());
        ClaimEligibility eligibility=mapper.findClaimEligibility(tenantId,exceptionId); if(eligibility==null)throw notFound();
        if(!"RESOLVED".equals(eligibility.exceptionStatus())||!Set.of("DELIVERED","RETURNED","LOST").contains(eligibility.orderStatus())
                ||!eligibility.orderCurrency().equals(currency))throw new ExceptionClaimException("CLAIM-1001",422);
        if(mapper.findClaimByException(tenantId,exceptionId)!=null)throw new ExceptionClaimException("CLAIM-1002",409);
        String no=number("CL",now());
        try{mapper.insertClaim(tenantId,exceptionId,no,request.claimAmount(),currency);}catch(DuplicateKeyException e){throw new ExceptionClaimException("CLAIM-1002",409);}
        ClaimRecord created=mapper.findClaimByNo(tenantId,no); if(created==null)throw new IllegalStateException("Claim was not created");
        ObjectNode detail=json.createObjectNode().put("exceptionId",exceptionId).put("currency",currency);
        audit(tenantId,operatorUserId,"CLAIM_CREATE","CLAIM_RECORD",created.id(),requestId,null,detail);
        mapper.completeIdempotency(tenantId,CREATE_CLAIM,key,"CLAIM_RECORD",created.id()); return claimResponse(created);
    }

    @Transactional
    public ClaimResponse submitClaim(Long tenantId,Long operatorUserId,Long claimId,ClaimActionRequest request,String requestId){
        ClaimRecord current=requireClaim(tenantId,claimId); LocalDateTime at=now();
        if(!ClaimStateMachine.canSubmit(current.status())||mapper.transitionClaim(tenantId,claimId,"OPEN","SUBMITTED",request.version(),at,null)!=1)throw new ExceptionClaimException("CLAIM-1004",409);
        auditTransition(tenantId,operatorUserId,claimId,requestId,request.reason(),"OPEN","SUBMITTED"); return getClaim(tenantId,claimId);
    }

    @Transactional
    public ClaimResponse resolveClaim(Long tenantId,Long operatorUserId,Long claimId,ClaimResultRequest request,String requestId){
        ClaimRecord current=requireClaim(tenantId,claimId); String target=upper(request.status());
        if(!ClaimStateMachine.canResolve(current.status(),target)||mapper.transitionClaim(tenantId,claimId,"SUBMITTED",target,request.version(),null,utc(request.resolvedAt()))!=1)throw new ExceptionClaimException("CLAIM-1004",409);
        auditTransition(tenantId,operatorUserId,claimId,requestId,request.reason(),"SUBMITTED",target); return getClaim(tenantId,claimId);
    }

    @Transactional
    public ClaimResponse closeClaim(Long tenantId,Long operatorUserId,Long claimId,ClaimActionRequest request,String requestId){
        ClaimRecord current=requireClaim(tenantId,claimId);
        if(!ClaimStateMachine.canClose(current.status())||mapper.transitionClaim(tenantId,claimId,current.status(),"CLOSED",request.version(),null,null)!=1)throw new ExceptionClaimException("CLAIM-1004",409);
        auditTransition(tenantId,operatorUserId,claimId,requestId,request.reason(),current.status(),"CLOSED"); return getClaim(tenantId,claimId);
    }

    private ExceptionClaimMapper.IdempotencyRecord claimIdempotency(Long tenantId,String operation,String key,String hash,String path){
        if(key==null||key.isBlank()||key.length()>128)throw badRequest(); var prior=mapper.findIdempotency(tenantId,operation,key);
        if(prior==null)try{mapper.insertIdempotency(tenantId,operation,key,path,hash,now().plusMinutes(30));}catch(DuplicateKeyException e){prior=mapper.findIdempotency(tenantId,operation,key);if(prior==null)throw e;}
        if(prior==null)return null; if(!hash.equals(prior.requestHash()))throw new ExceptionClaimException("COMMON-1009",409); if(prior.resourceId()==null)throw new ExceptionClaimException("COMMON-1010",409); return prior;
    }
    private ExceptionCase requireException(Long t,Long id){tenant(t);ExceptionCase value=mapper.findException(t,id);if(value==null)throw notFound();return value;}
    private ClaimRecord requireClaim(Long t,Long id){tenant(t);ClaimRecord value=mapper.findClaim(t,id);if(value==null)throw notFound();return value;}
    private void auditTransition(Long t,Long u,Long id,String requestId,String reason,String from,String to){ObjectNode d=json.createObjectNode().put("fromStatus",from).put("toStatus",to);audit(t,u,"CLAIM_"+to,"CLAIM_RECORD",id,requestId,reason,d);}
    private void audit(Long t,Long u,String action,String resource,Long id,String requestId,String reason,ObjectNode detail){try{mapper.insertAudit(t,u,action,resource,id,requestId,reason,json.writeValueAsString(detail),now());}catch(Exception e){throw new IllegalStateException("Audit detail serialization failed",e);}}
    private ExceptionCaseResponse response(ExceptionCase e,ClaimRecord claim){return new ExceptionCaseResponse(e.id(),e.orderId(),e.exceptionNo(),e.exceptionType(),e.status(),e.description(),offset(e.reportedAt()),e.assignedToUserId(),e.version(),offset(e.createdAt()),offset(e.updatedAt()),claim==null?null:claimResponse(claim));}
    private ClaimResponse claimResponse(ClaimRecord c){return new ClaimResponse(c.id(),c.exceptionCaseId(),c.claimNo(),c.status(),c.claimAmount(),c.currency(),offset(c.submittedAt()),offset(c.resolvedAt()),c.version(),offset(c.createdAt()),offset(c.updatedAt()));}
    private String optionalStatus(String s,Set<String> allowed){if(s==null||s.isBlank())return null;String v=upper(s);if(!allowed.contains(v))throw badRequest();return v;}
    private String number(String prefix,LocalDateTime at){return prefix+DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(at)+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String upper(String v){return v==null?"":v.trim().toUpperCase(Locale.ROOT);} private LocalDateTime now(){return LocalDateTime.now(clock);}
    private LocalDateTime utc(OffsetDateTime v){return LocalDateTime.ofInstant(v.toInstant(),ZoneOffset.UTC);} private OffsetDateTime offset(LocalDateTime v){return v==null?null:v.atOffset(ZoneOffset.UTC);}
    private void tenant(Long t){if(t==null||t<1)throw new ExceptionClaimException("COMMON-1004",403);} private ExceptionClaimException notFound(){return new ExceptionClaimException("COMMON-1006",404);} private ExceptionClaimException badRequest(){return new ExceptionClaimException("COMMON-1001",400);}
}
