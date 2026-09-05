package com.shipflow.exceptioncase.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shipflow.exceptioncase.api.model.*;
import com.shipflow.exceptioncase.domain.*;
import com.shipflow.exceptioncase.mapper.ExceptionClaimMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExceptionClaimApplicationService {
    private static final Set<String> EXCEPTION_TYPES = Set.of("ADDRESS", "CUSTOMS", "TRANSPORT", "OTHER");
    private static final Set<String> EXCEPTION_STATUSES = Set.of("OPEN", "PROCESSING", "WAITING_PROVIDER_FEEDBACK",
            "RESOLVED", "PENDING_FINANCE_CONFIRMATION", "CLOSED");
    private static final Set<String> RESPONSIBLE_PARTIES = Set.of("MERCHANT", "PROVIDER", "CUSTOMS", "CUSTOMER", "OTHER");
    private static final Set<String> RECORD_TYPES = Set.of("CONTACT", "FOLLOW_UP", "PROVIDER_FEEDBACK", "INTERNAL_NOTE", "OTHER");
    private static final long MAX_EVIDENCE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> EVIDENCE_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain");
    private static final String CREATE_EXCEPTION = "createExceptionCase";
    private static final String CREATE_CLAIM = "createClaimRecord";
    private static final String CREATE_HANDLING = "createExceptionHandlingRecord";
    private static final String CREATE_EVIDENCE = "createExceptionEvidence";
    private static final String ASSIGN_EXCEPTION = "assignExceptionCase";
    private static final String TRANSITION_EXCEPTION = "transitionExceptionCase";
    private static final String SUBMIT_CLAIM = "submitClaim";
    private static final String RESOLVE_CLAIM = "resolveClaim";
    private static final String FINANCE_CONFIRM_CLAIM = "financeConfirmClaim";
    private static final String CLOSE_CLAIM = "closeClaim";

    private final ExceptionClaimMapper mapper;
    private final StoreScopeMapper storeScope;
    private final ObjectMapper json;
    private final Clock clock;

    public ExceptionClaimApplicationService(ExceptionClaimMapper mapper, StoreScopeMapper storeScope,
                                            ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.storeScope = storeScope;
        this.json = json;
        this.clock = clock;
    }

    public ExceptionCasePageResponse list(Long tenantId, Long userId, Long orderId, String status, int page, int pageSize) { return list(tenantId,userId,orderId,status,null,page,pageSize); }
    public ExceptionCasePageResponse list(Long tenantId, Long userId, Long orderId, String status, String workbenchFilter, int page, int pageSize) {
        tenant(tenantId, userId);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw badRequest();
        String normalized = optionalStatus(status, EXCEPTION_STATUSES);
        String filter = workbenchFilter == null || workbenchFilter.isBlank() ? null : workbenchFilter.trim().toUpperCase(Locale.ROOT);
        if (filter != null && !"PENDING_FOLLOW_UP".equals(filter)) throw badRequest();
        if (orderId != null) requireOrderStore(tenantId, userId, orderId);
        long total = filter == null ? mapper.countAccessibleExceptions(tenantId, userId, orderId, normalized)
                : mapper.countAccessibleExceptions(tenantId, userId, orderId, normalized, filter);
        var items = (filter == null ? mapper.findAccessibleExceptions(tenantId, userId, orderId, normalized,
                        (page - 1) * pageSize, pageSize) : mapper.findAccessibleExceptions(tenantId, userId, orderId, normalized, filter,
                        (page - 1) * pageSize, pageSize)).stream()
                .map(e -> response(e, null)).toList();
        return new ExceptionCasePageResponse(page, pageSize, (total + pageSize - 1) / pageSize, total, items);
    }

    public ExceptionCasePageResponse list(Long tenantId, Long userId, Long orderId, String status,
                                          String workbenchFilter, String exceptionType, String orderNo,
                                          Long storeId, String responsibleParty, OffsetDateTime createdFrom,
                                          OffsetDateTime createdTo, int page, int pageSize,
                                          String sortBy, String sortDirection) {
        tenant(tenantId, userId);
        if (page < 1 || pageSize < 1 || pageSize > 100) throw badRequest();
        String normalizedStatus = optionalStatus(status, EXCEPTION_STATUSES);
        String normalizedType = optionalStatus(exceptionType, EXCEPTION_TYPES);
        String normalizedParty = optionalStatus(responsibleParty, RESPONSIBLE_PARTIES);
        String filter = workbenchFilter == null || workbenchFilter.isBlank() ? null : upper(workbenchFilter);
        if (filter != null && !"PENDING_FOLLOW_UP".equals(filter)) throw badRequest();
        String normalizedOrderNo = orderNo == null || orderNo.isBlank() ? null : orderNo.trim();
        if (normalizedOrderNo != null && normalizedOrderNo.length() > 64) throw badRequest();
        LocalDateTime from = createdFrom == null ? null : utc(createdFrom);
        LocalDateTime to = createdTo == null ? null : utc(createdTo);
        if (from != null && to != null && from.isAfter(to)) throw badRequest();
        String sortKey = switch (sortBy == null ? "createdAt" : sortBy) {
            case "createdAt" -> "CREATED_AT"; case "updatedAt" -> "UPDATED_AT";
            case "status" -> "STATUS"; case "exceptionType" -> "EXCEPTION_TYPE";
            case "orderNo" -> "ORDER_NO"; default -> throw badRequest();
        };
        String direction = sortDirection == null ? "DESC" : upper(sortDirection);
        if (!Set.of("ASC", "DESC").contains(direction)) throw badRequest();
        if (orderId != null) requireOrderStore(tenantId, userId, orderId);
        if (storeId != null) requireStoreAccess(tenantId, userId, storeId);
        long total = mapper.countAccessibleExceptionsFiltered(tenantId, userId, orderId, normalizedStatus,
                filter, normalizedType, normalizedOrderNo, storeId, normalizedParty, from, to);
        List<ExceptionCaseResponse> items = mapper.findAccessibleExceptionsFiltered(tenantId, userId, orderId,
                        normalizedStatus, filter, normalizedType, normalizedOrderNo, storeId, normalizedParty,
                        from, to, sortKey, direction, (page - 1) * pageSize, pageSize).stream()
                .map(e -> response(e, mapper.findClaimByException(tenantId, e.id()))).toList();
        return new ExceptionCasePageResponse(page, pageSize, (total + pageSize - 1) / pageSize, total, items);
    }

    public ExceptionCaseResponse get(Long tenantId, Long userId, Long exceptionId) {
        ExceptionCase e = requireException(tenantId, userId, exceptionId);
        ClaimRecord claim = mapper.findClaimByException(tenantId, exceptionId);
        return response(e, claim, mapper.findHandlingRecords(tenantId, userId, exceptionId),
                mapper.findEvidenceAttachments(tenantId, userId, exceptionId),
                mapper.findExceptionTimeline(tenantId, exceptionId));
    }

    public ClaimResponse getClaim(Long tenantId, Long userId, Long claimId) {
        return claimResponse(requireClaim(tenantId, userId, claimId));
    }

    @Transactional
    public ExceptionCaseResponse createException(Long tenantId, Long operatorUserId, Long orderId,
                                                  CreateExceptionRequest request, String key, String requestId) {
        tenant(tenantId, operatorUserId);
        String type = upper(request.exceptionType());
        if (!EXCEPTION_TYPES.contains(type)) throw new ExceptionClaimException("EXCEPTION-1001", 422);
        Long storeId = requireOrderStore(tenantId, operatorUserId, orderId);
        String hash = sha256(orderId + "\n" + type + "\n" + request.description() + "\n"
                + request.reportedAt().toInstant() + "\n" + request.trackingEventId());
        var prior = claimIdempotency(tenantId, CREATE_EXCEPTION, key, hash, "/api/v1/orders/{orderId}/exceptions");
        if (prior != null) return get(tenantId, operatorUserId, prior.resourceId());
        if (!mapper.orderExists(tenantId, orderId) || storeId == null) throw notFound();
        if (request.trackingEventId() != null && !mapper.trackingEventExists(tenantId, orderId, request.trackingEventId())) {
            throw notFound();
        }
        String no = number("EX", now());
        mapper.insertException(tenantId, orderId, no, type, request.description(), utc(request.reportedAt()));
        ExceptionCase created = mapper.findExceptionByNo(tenantId, no);
        if (created == null) throw new IllegalStateException("Exception case was not created");
        ObjectNode detail = json.createObjectNode().put("orderId", orderId).put("storeId", storeId).put("exceptionType", type);
        if (request.trackingEventId() != null) detail.put("trackingEventId", request.trackingEventId());
        audit(tenantId, operatorUserId, "EXCEPTION_CREATE", "EXCEPTION_CASE", created.id(), requestId, null, detail);
        mapper.completeIdempotency(tenantId, CREATE_EXCEPTION, key, "EXCEPTION_CASE", created.id());
        return response(created, null);
    }

    @Transactional
    public ExceptionCaseResponse assign(Long tenantId, Long operatorUserId, Long exceptionId,
                                        AssignExceptionRequest request, String key, String requestId) {
        ExceptionCase current = requireException(tenantId, operatorUserId, exceptionId);
        String party = upper(request.responsibleParty());
        if (!RESPONSIBLE_PARTIES.contains(party)) throw new ExceptionClaimException("EXCEPTION-1003", 422);
        String hash = sha256(exceptionId + "\n" + request.assignedToUserId() + "\n" + party + "\n" + Objects.toString(request.reason(), "") + "\n" + request.version());
        var prior = claimIdempotency(tenantId, ASSIGN_EXCEPTION, key, hash, "/api/v1/exceptions/{exceptionId}/assign");
        if (prior != null) return get(tenantId, operatorUserId, prior.resourceId());
        if (!mapper.activeTenantUserExists(tenantId, request.assignedToUserId())
                || !storeScope.canAccessStore(tenantId, request.assignedToUserId(), current.storeId())) throw notFound();
        if (!ExceptionStateMachine.canAssign(current.status())
                || mapper.assignException(tenantId, exceptionId, request.version(), request.assignedToUserId(), party) != 1) {
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
        }
        String after = "OPEN".equals(current.status()) ? "PROCESSING" : current.status();
        ObjectNode detail = json.createObjectNode().put("oldAssignedToUserId", current.assignedToUserId() == null ? 0 : current.assignedToUserId()).put("assignedToUserId", request.assignedToUserId())
                .put("responsibleParty", party).put("fromStatus", current.status()).put("toStatus", after);
        audit(tenantId, operatorUserId, "EXCEPTION_ASSIGN", "EXCEPTION_CASE", exceptionId, requestId, request.reason(), detail);
        mapper.completeIdempotency(tenantId, ASSIGN_EXCEPTION, key, "EXCEPTION_CASE", exceptionId);
        return get(tenantId, operatorUserId, exceptionId);
    }

    public ExceptionCaseResponse assign(Long tenantId, Long operatorUserId, Long exceptionId,
                                        AssignExceptionRequest request, String requestId) {
        throw new ExceptionClaimException("COMMON-1001", 400);
    }

    @Transactional
    public ExceptionCaseResponse transitionException(Long tenantId, Long operatorUserId, Long exceptionId,
                                                      ExceptionStatusRequest request, String key, String requestId) {
        ExceptionCase current = requireException(tenantId, operatorUserId, exceptionId);
        String target = upper(request.status());
        String hash = sha256(exceptionId + "\n" + target + "\n" + request.reason() + "\n" + request.version());
        var prior = claimIdempotency(tenantId, TRANSITION_EXCEPTION, key, hash, "/api/v1/exceptions/{exceptionId}/status");
        if (prior != null) return get(tenantId, operatorUserId, prior.resourceId());
        if (!ExceptionStateMachine.canTransition(current.status(), target))
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
        if ("RESOLVED".equals(target) && !mapper.exceptionResolutionReady(tenantId, exceptionId))
            throw new ExceptionClaimException("EXCEPTION-1004", 422);
        if ("CLOSED".equals(target) && !mapper.exceptionCloseReady(tenantId, exceptionId))
            throw new ExceptionClaimException("EXCEPTION-1004", 422);
        if (mapper.transitionException(tenantId, exceptionId, current.status(), target, request.version(), null) != 1) {
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
        }
        ObjectNode detail = json.createObjectNode().put("fromStatus", current.status()).put("toStatus", target);
        audit(tenantId, operatorUserId, "EXCEPTION_" + target, "EXCEPTION_CASE", exceptionId, requestId, request.reason(), detail);
        mapper.completeIdempotency(tenantId, TRANSITION_EXCEPTION, key, "EXCEPTION_CASE", exceptionId);
        return get(tenantId, operatorUserId, exceptionId);
    }

    public ExceptionCaseResponse transitionException(Long tenantId, Long operatorUserId, Long exceptionId,
                                                      ExceptionStatusRequest request, String requestId) {
        throw new ExceptionClaimException("COMMON-1001", 400);
    }

    public List<HandlingRecordResponse> listHandlingRecords(Long tenantId, Long userId, Long exceptionId) {
        requireException(tenantId, userId, exceptionId);
        return mapper.findHandlingRecords(tenantId, userId, exceptionId).stream().map(this::handlingResponse).toList();
    }

    @Transactional
    public HandlingRecordResponse addHandlingRecord(Long tenantId, Long operatorUserId, Long exceptionId,
                                                    CreateHandlingRecordRequest request, String key, String requestId) {
        ExceptionCase current = requireException(tenantId, operatorUserId, exceptionId);
        String type = upper(request.recordType());
        if (!RECORD_TYPES.contains(type)) throw new ExceptionClaimException("EXCEPTION-1003", 422);
        String hash = sha256(exceptionId + "\n" + type + "\n" + request.content());
        var prior = claimIdempotency(tenantId, CREATE_HANDLING, key,
                sha256(hash + "\n" + request.version()), "/api/v1/exceptions/{exceptionId}/handling-records");
        if (prior != null) return handlingResponse(mapper.findHandlingRecord(tenantId, prior.resourceId()));
        if ("CLOSED".equals(current.status())) throw new ExceptionClaimException("EXCEPTION-1002", 409);
        if (mapper.touchException(tenantId, exceptionId, request.version()) != 1)
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
        String recordNo = number("EH", now());
        mapper.insertHandlingRecord(tenantId, exceptionId, recordNo, operatorUserId, type, request.content(), now());
        HandlingRecord created = mapper.findHandlingRecordByNo(tenantId, exceptionId, recordNo);
        if (created == null) throw new IllegalStateException("Handling record was not created");
        ObjectNode detail = json.createObjectNode().put("exceptionId", exceptionId).put("recordId", created.id()).put("recordType", type);
        audit(tenantId, operatorUserId, "EXCEPTION_HANDLING_RECORD_CREATE", "EXCEPTION_HANDLING_RECORD",
                created.id(), requestId, null, detail);
        mapper.completeIdempotency(tenantId, CREATE_HANDLING, key, "EXCEPTION_HANDLING_RECORD", created.id());
        return handlingResponse(created);
    }

    public List<EvidenceAttachmentResponse> listEvidence(Long tenantId, Long userId, Long exceptionId) {
        requireException(tenantId, userId, exceptionId);
        return mapper.findEvidenceAttachments(tenantId, userId, exceptionId).stream().map(this::evidenceResponse).toList();
    }

    @Transactional
    public EvidenceAttachmentResponse uploadEvidence(Long tenantId, Long operatorUserId, Long exceptionId,
                                                     MultipartFile file, String key, String requestId) {
        return uploadEvidence(tenantId, operatorUserId, exceptionId, file, null, key, requestId);
    }

    @Transactional
    public EvidenceAttachmentResponse uploadEvidence(Long tenantId, Long operatorUserId, Long exceptionId,
                                                     MultipartFile file, String description, String key, String requestId) {
        return uploadEvidence(tenantId, operatorUserId, exceptionId, file, description, null, key, requestId);
    }

    @Transactional
    public EvidenceAttachmentResponse uploadEvidence(Long tenantId, Long operatorUserId, Long exceptionId,
                                                     MultipartFile file, String description, Long version,
                                                     String key, String requestId) {
        ExceptionCase current = requireException(tenantId, operatorUserId, exceptionId);
        String normalizedDescription = description == null ? null : description.trim();
        if (normalizedDescription != null && normalizedDescription.length() > 1000) throw badRequest();
        String contentType = normalizedContentType(file);
        byte[] content = readEvidence(file);
        String contentHash = sha256(content);
        var prior = claimIdempotency(tenantId, CREATE_EVIDENCE, key,
                sha256(exceptionId + "\n" + contentHash + "\n" + Objects.toString(normalizedDescription, "") + "\n" + Objects.toString(version, String.valueOf(current.version()))), "/api/v1/exceptions/{exceptionId}/evidence");
        if (prior != null) return evidenceResponse(mapper.findEvidenceById(tenantId, prior.resourceId()));
        if ("CLOSED".equals(current.status())) throw new ExceptionClaimException("EXCEPTION-1002", 409);
        EvidenceAttachment existing = mapper.findEvidenceByHash(tenantId, exceptionId, contentHash);
        if (existing != null) {
            mapper.completeIdempotency(tenantId, CREATE_EVIDENCE, key, "EXCEPTION_EVIDENCE_ATTACHMENT", existing.id());
            return evidenceResponse(existing);
        }
        Long expectedVersion = version == null ? current.version() : version;
        if (mapper.touchException(tenantId, exceptionId, expectedVersion) != 1)
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
        String fileName = safeFileName(file.getOriginalFilename());
        try {
            mapper.insertEvidence(tenantId, exceptionId, operatorUserId, fileName, contentType, (long) content.length,
                    contentHash, content, normalizedDescription, now());
        } catch (DuplicateKeyException duplicate) {
            existing = mapper.findEvidenceByHash(tenantId, exceptionId, contentHash);
            if (existing == null) throw duplicate;
            mapper.completeIdempotency(tenantId, CREATE_EVIDENCE, key, "EXCEPTION_EVIDENCE_ATTACHMENT", existing.id());
            return evidenceResponse(existing);
        }
        EvidenceAttachment created = mapper.findEvidenceByHash(tenantId, exceptionId, contentHash);
        if (created == null) throw new IllegalStateException("Evidence attachment was not created");
        ObjectNode detail = json.createObjectNode().put("exceptionId", exceptionId).put("attachmentId", created.id())
                .put("contentSha256", contentHash).put("fileSize", content.length);
        audit(tenantId, operatorUserId, "EXCEPTION_EVIDENCE_UPLOAD", "EXCEPTION_EVIDENCE_ATTACHMENT",
                created.id(), requestId, null, detail);
        mapper.completeIdempotency(tenantId, CREATE_EVIDENCE, key, "EXCEPTION_EVIDENCE_ATTACHMENT", created.id());
        return evidenceResponse(created);
    }

    public EvidenceAttachment downloadEvidence(Long tenantId, Long userId, Long exceptionId, Long attachmentId) {
        EvidenceAttachment attachment = mapper.findEvidenceById(tenantId, attachmentId);
        if (attachment == null || !exceptionId.equals(attachment.exceptionCaseId())) throw notFound();
        requireException(tenantId, userId, exceptionId);
        return attachment;
    }

    @Transactional
    public ClaimResponse createClaim(Long tenantId, Long operatorUserId, Long exceptionId,
                                     CreateClaimRequest request, String key, String requestId) {
        ExceptionCase current = requireException(tenantId, operatorUserId, exceptionId);
        String currency = upper(request.currency());
        if (request.claimAmount() == null || request.claimAmount().signum() <= 0 || request.claimAmount().scale() > 2) {
            throw new ExceptionClaimException("CLAIM-1003", 422);
        }
        String hash = sha256(exceptionId + "\n" + request.claimAmount().stripTrailingZeros().toPlainString() + "\n" + currency
                + "\n" + request.claimReason() + "\n" + request.exceptionVersion() + "\n" + Objects.toString(request.evidenceAttachmentIds(), ""));
        var prior = claimIdempotency(tenantId, CREATE_CLAIM, key, hash, "/api/v1/exceptions/{exceptionId}/claim");
        if (prior != null) return getClaim(tenantId, operatorUserId, prior.resourceId());
        if ("CLOSED".equals(current.status())) throw new ExceptionClaimException("EXCEPTION-1002", 409);
        ClaimEligibility eligibility = mapper.findClaimEligibility(tenantId, exceptionId);
        if (eligibility == null) throw notFound();
        if (!"PROCESSING".equals(eligibility.exceptionStatus()) || !Set.of("OUTBOUND", "DELIVERED", "RETURNED", "LOST").contains(eligibility.orderStatus())
                || !eligibility.orderCurrency().equals(currency)) throw new ExceptionClaimException("CLAIM-1001", 422);
        if (mapper.findClaimByException(tenantId, exceptionId) != null) throw new ExceptionClaimException("CLAIM-1002", 409);
        String no = number("CL", now());
        if (request.evidenceAttachmentIds() == null || request.evidenceAttachmentIds().isEmpty())
            throw new ExceptionClaimException("CLAIM-1003", 422);
        for (Long evidenceId : new LinkedHashSet<>(request.evidenceAttachmentIds()))
            if (!mapper.evidenceBelongsToException(tenantId, exceptionId, evidenceId)) throw notFound();
        if (mapper.transitionException(tenantId, exceptionId, current.status(), "PENDING_FINANCE_CONFIRMATION",
                request.exceptionVersion(), null) != 1) throw new ExceptionClaimException("EXCEPTION-1002", 409);
        try { mapper.insertClaim(tenantId, exceptionId, no, request.claimAmount(), currency, request.claimReason()); }
        catch (DuplicateKeyException e) { throw new ExceptionClaimException("CLAIM-1002", 409); }
        ClaimRecord created = mapper.findClaimByNo(tenantId, no);
        if (created == null) throw new IllegalStateException("Claim was not created");
        for (Long evidenceId : new LinkedHashSet<>(request.evidenceAttachmentIds()))
            mapper.insertClaimEvidenceReference(tenantId, created.id(), evidenceId);
        ObjectNode detail = json.createObjectNode().put("exceptionId", exceptionId).put("currency", currency);
        audit(tenantId, operatorUserId, "EXCEPTION_PENDING_FINANCE_CONFIRMATION", "EXCEPTION_CASE", exceptionId,
                requestId, request.claimReason(), json.createObjectNode().put("fromStatus", current.status())
                        .put("toStatus", "PENDING_FINANCE_CONFIRMATION").put("claimId", created.id()));
        audit(tenantId, operatorUserId, "CLAIM_CREATE", "CLAIM_RECORD", created.id(), requestId, null, detail);
        mapper.completeIdempotency(tenantId, CREATE_CLAIM, key, "CLAIM_RECORD", created.id());
        return claimResponse(created);
    }

    @Transactional
    public ClaimResponse submitClaim(Long tenantId, Long operatorUserId, Long claimId, ClaimActionRequest request, String key, String requestId) {
        ClaimRecord current = requireClaim(tenantId, operatorUserId, claimId); LocalDateTime at = now();
        var prior = claimIdempotency(tenantId, SUBMIT_CLAIM, key, sha256(claimId+"\n"+request.version()+"\n"+Objects.toString(request.reason(),"")), "/api/v1/claims/{claimId}/submit");
        if (prior != null) return getClaim(tenantId, operatorUserId, prior.resourceId());
        ensureParentExceptionMutable(tenantId, operatorUserId, current);
        if (!ClaimStateMachine.canSubmit(current.status()) || mapper.transitionClaim(tenantId, claimId, "OPEN", "SUBMITTED", request.version(), at, null) != 1)
            throw new ExceptionClaimException("CLAIM-1004", 409);
        auditTransition(tenantId, operatorUserId, claimId, requestId, request.reason(), "OPEN", "SUBMITTED");
        mapper.completeIdempotency(tenantId, SUBMIT_CLAIM, key, "CLAIM_RECORD", claimId);
        return getClaim(tenantId, operatorUserId, claimId);
    }

    public ClaimResponse submitClaim(Long t, Long u, Long id, ClaimActionRequest r, String requestId) { throw badRequest(); }

    @Transactional
    public ClaimResponse resolveClaim(Long tenantId, Long operatorUserId, Long claimId, ClaimResultRequest request, String key, String requestId) {
        ClaimRecord current = requireClaim(tenantId, operatorUserId, claimId); String target = upper(request.status());
        BigDecimal approved = request.approvedAmount();
        if (("APPROVED".equals(target) && (approved == null || approved.compareTo(current.claimAmount()) != 0))
                || ("PARTIALLY_APPROVED".equals(target) && (approved == null || approved.signum() <= 0 || approved.compareTo(current.claimAmount()) >= 0))
                || ("REJECTED".equals(target) && approved != null && approved.signum() != 0))
            throw new ExceptionClaimException("CLAIM-1003", 422);
        var prior = claimIdempotency(tenantId, RESOLVE_CLAIM, key, sha256(claimId+"\n"+target+"\n"+approved+"\n"+request.reason()+"\n"+request.version()), "/api/v1/claims/{claimId}/result");
        if (prior != null) return getClaim(tenantId, operatorUserId, prior.resourceId());
        ensureParentExceptionMutable(tenantId, operatorUserId, current);
        if (!ClaimStateMachine.canResolve(current.status(), target) || mapper.resolveClaim(tenantId, claimId, "SUBMITTED", target,
                request.version(), approved, request.reason(), utc(request.resolvedAt())) != 1) throw new ExceptionClaimException("CLAIM-1004", 409);
        auditTransition(tenantId, operatorUserId, claimId, requestId, request.reason(), "SUBMITTED", target);
        mapper.completeIdempotency(tenantId, RESOLVE_CLAIM, key, "CLAIM_RECORD", claimId);
        return getClaim(tenantId, operatorUserId, claimId);
    }

    public ClaimResponse resolveClaim(Long t, Long u, Long id, ClaimResultRequest r, String requestId) { throw badRequest(); }

    @Transactional
    public ClaimResponse financeConfirmClaim(Long tenantId, Long operatorUserId, Long claimId,
                                             FinanceConfirmRequest request, String key, String requestId) {
        ClaimRecord current = requireClaim(tenantId, operatorUserId, claimId);
        if (!Set.of("APPROVED", "PARTIALLY_APPROVED", "REJECTED").contains(current.status()))
            throw new ExceptionClaimException("CLAIM-1004", 409);
        var prior = claimIdempotency(tenantId, FINANCE_CONFIRM_CLAIM, key,
                sha256(claimId+"\n"+request.version()+"\n"+Objects.toString(request.reason(),"")), "/api/v1/claims/{claimId}/finance-confirmation");
        if (prior != null) return getClaim(tenantId, operatorUserId, prior.resourceId());
        ensureParentExceptionMutable(tenantId, operatorUserId, current);
        if (mapper.financeConfirmClaim(tenantId, claimId, request.version(), operatorUserId, now()) != 1)
            throw new ExceptionClaimException("CLAIM-1004", 409);
        auditTransition(tenantId, operatorUserId, claimId, requestId, request.reason(), current.status(), "FINANCE_CONFIRMED");
        mapper.completeIdempotency(tenantId, FINANCE_CONFIRM_CLAIM, key, "CLAIM_RECORD", claimId);
        return getClaim(tenantId, operatorUserId, claimId);
    }

    @Transactional
    public ClaimResponse closeClaim(Long tenantId, Long operatorUserId, Long claimId, ClaimActionRequest request, String key, String requestId) {
        ClaimRecord current = requireClaim(tenantId, operatorUserId, claimId);
        if (current.financeConfirmedAt() == null) throw new ExceptionClaimException("CLAIM-1005", 422);
        var prior = claimIdempotency(tenantId, CLOSE_CLAIM, key, sha256(claimId+"\n"+request.version()+"\n"+Objects.toString(request.reason(),"")), "/api/v1/claims/{claimId}/close");
        if (prior != null) return getClaim(tenantId, operatorUserId, prior.resourceId());
        ensureParentExceptionMutable(tenantId, operatorUserId, current);
        if (!ClaimStateMachine.canClose(current.status()) || mapper.transitionClaim(tenantId, claimId, current.status(), "CLOSED", request.version(), null, null) != 1)
            throw new ExceptionClaimException("CLAIM-1004", 409);
        auditTransition(tenantId, operatorUserId, claimId, requestId, request.reason(), current.status(), "CLOSED");
        mapper.completeIdempotency(tenantId, CLOSE_CLAIM, key, "CLAIM_RECORD", claimId);
        return getClaim(tenantId, operatorUserId, claimId);
    }

    public ClaimResponse closeClaim(Long t, Long u, Long id, ClaimActionRequest r, String requestId) { throw badRequest(); }

    private ExceptionClaimMapper.IdempotencyRecord claimIdempotency(Long tenantId, String operation, String key, String hash, String path) {
        if (key == null || key.isBlank() || key.length() > 128) throw badRequest();
        var prior = mapper.findIdempotency(tenantId, operation, key);
        if (prior == null) try { mapper.insertIdempotency(tenantId, operation, key, path, hash, now().plusMinutes(30)); }
        catch (DuplicateKeyException e) { prior = mapper.findIdempotency(tenantId, operation, key); if (prior == null) throw e; }
        if (prior == null) return null;
        if (!hash.equals(prior.requestHash())) throw new ExceptionClaimException("COMMON-1009", 409);
        if (prior.resourceId() == null) throw new ExceptionClaimException("COMMON-1010", 409);
        return prior;
    }

    private ExceptionCase requireException(Long tenantId, Long userId, Long exceptionId) {
        tenant(tenantId, userId); ExceptionCase value = mapper.findException(tenantId, exceptionId);
        if (value == null) throw notFound(); requireStoreAccess(tenantId, userId, value.storeId()); return value;
    }
    private ClaimRecord requireClaim(Long tenantId, Long userId, Long claimId) {
        tenant(tenantId, userId); ClaimRecord value = mapper.findClaim(tenantId, claimId);
        if (value == null) throw notFound(); requireException(tenantId, userId, value.exceptionCaseId()); return value;
    }
    private void ensureParentExceptionMutable(Long tenantId, Long userId, ClaimRecord claim) {
        if ("CLOSED".equals(requireException(tenantId, userId, claim.exceptionCaseId()).status()))
            throw new ExceptionClaimException("EXCEPTION-1002", 409);
    }
    private Long requireOrderStore(Long tenantId, Long userId, Long orderId) {
        tenant(tenantId, userId); Long storeId = mapper.findOrderStoreId(tenantId, orderId);
        if (storeId == null) throw notFound(); requireStoreAccess(tenantId, userId, storeId); return storeId;
    }
    private void requireStoreAccess(Long tenantId, Long userId, Long storeId) {
        if (storeId == null || userId == null || !storeScope.canAccessStore(tenantId, userId, storeId)) throw notFound();
    }
    private void tenant(Long tenantId, Long userId) { if (tenantId == null || tenantId < 1 || userId == null || userId < 1) throw new ExceptionClaimException("COMMON-1004", 403); }
    private ExceptionClaimException notFound() { return new ExceptionClaimException("COMMON-1006", 404); }
    private ExceptionClaimException badRequest() { return new ExceptionClaimException("COMMON-1001", 400); }
    private void auditTransition(Long t, Long u, Long id, String requestId, String reason, String from, String to) { ObjectNode d=json.createObjectNode().put("fromStatus",from).put("toStatus",to); audit(t,u,"CLAIM_"+to,"CLAIM_RECORD",id,requestId,reason,d); }
    private void audit(Long t, Long u, String action, String resource, Long id, String requestId, String reason, ObjectNode detail) { try { mapper.insertAudit(t,u,action,resource,id,correlationId(requestId),reason,json.writeValueAsString(detail),now()); } catch(Exception e) { throw new IllegalStateException("Audit detail serialization failed",e); } }
    private ExceptionCaseResponse response(ExceptionCase e, ClaimRecord claim) { return response(e, claim, List.of(), List.of(), List.of()); }
    private ExceptionCaseResponse response(ExceptionCase e, ClaimRecord claim, List<HandlingRecord> handling,
                                           List<EvidenceAttachment> evidence, List<ExceptionTimelineEvent> timeline) {
        return new ExceptionCaseResponse(e.id(),e.orderId(),e.storeId(),e.exceptionNo(),e.exceptionType(),e.status(),e.description(),
                offset(e.reportedAt()),e.assignedToUserId(),e.version(),offset(e.createdAt()),offset(e.updatedAt()),e.responsibleParty(),
                claim==null?null:claimResponse(claim),e.orderNo(),e.storeName(),e.assignedToUserName(),
                handling.stream().map(this::handlingResponse).toList(),evidence.stream().map(this::evidenceResponse).toList(),
                timeline.stream().map(this::timelineResponse).toList());
    }
    private HandlingRecordResponse handlingResponse(HandlingRecord r) { if(r==null) throw new IllegalStateException("Handling record was not found"); return new HandlingRecordResponse(r.id(),r.exceptionCaseId(),r.recordNo(),r.handledByUserId(),r.recordType(),r.content(),offset(r.createdAt())); }
    private EvidenceAttachmentResponse evidenceResponse(EvidenceAttachment a) { if(a==null) throw new IllegalStateException("Evidence attachment was not found"); return new EvidenceAttachmentResponse(a.id(),a.exceptionCaseId(),a.uploadedByUserId(),a.originalFileName(),a.contentType(),a.fileSize(),a.contentSha256(),a.description(),offset(a.createdAt())); }
    private ClaimResponse claimResponse(ClaimRecord c) { return new ClaimResponse(c.id(),c.exceptionCaseId(),c.claimNo(),c.status(),c.claimAmount(),c.currency(),offset(c.submittedAt()),offset(c.resolvedAt()),c.version(),offset(c.createdAt()),offset(c.updatedAt()),c.claimReason(),c.resolvedAmount(),c.resultReason(),c.financeConfirmedByUserId(),c.financeConfirmedByUserName(),offset(c.financeConfirmedAt()),mapper.findClaimEvidenceIds(c.tenantId(),c.id())); }
    private ExceptionTimelineEventResponse timelineResponse(ExceptionTimelineEvent e) { return new ExceptionTimelineEventResponse(e.eventType(),e.title(),e.description(),e.operatorUserId(),e.operatorName(),e.source(),offset(e.occurredAt()),e.statusBefore(),e.statusAfter(),e.relatedId(),e.requestId()); }
    private String correlationId(String requestId) { return requestId == null || requestId.isBlank() ? com.shipflow.common.trace.TraceId.currentOrCreate() : requestId; }
    private String optionalStatus(String s, Set<String> allowed) { if(s==null||s.isBlank()) return null; String v=upper(s); if(!allowed.contains(v)) throw badRequest(); return v; }
    private String number(String prefix, LocalDateTime at) { return prefix+DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(at)+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT); }
    private String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch(Exception e) { throw new IllegalStateException(e); } }
    private byte[] readEvidence(MultipartFile file) { if(file==null||file.isEmpty()||file.getSize()>MAX_EVIDENCE_BYTES) throw badRequest(); try { return file.getBytes(); } catch(IOException e) { throw badRequest(); } }
    private String normalizedContentType(MultipartFile file) { String value=file==null?null:file.getContentType(); if(value==null || !EVIDENCE_CONTENT_TYPES.contains(value.toLowerCase(Locale.ROOT))) throw new ExceptionClaimException("EXCEPTION-1003", 422); return value.toLowerCase(Locale.ROOT); }
    private String safeFileName(String name) { String value=name==null?"evidence.bin":name.replace('\\','/'); int slash=value.lastIndexOf('/'); value=slash>=0?value.substring(slash+1):value; value=value.replaceAll("[\\p{Cntrl}]", "").trim(); if(value.isBlank()||value.length()>255 || value.equals(".") || value.equals("..")) throw badRequest(); return value; }
    private String upper(String v) { return v==null?"":v.trim().toUpperCase(Locale.ROOT); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private LocalDateTime utc(OffsetDateTime v) { return LocalDateTime.ofInstant(v.toInstant(), ZoneOffset.UTC); }
    private OffsetDateTime offset(LocalDateTime v) { return v==null?null:v.atOffset(ZoneOffset.UTC); }
}
