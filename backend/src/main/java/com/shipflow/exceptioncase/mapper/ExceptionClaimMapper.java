package com.shipflow.exceptioncase.mapper;

import com.shipflow.exceptioncase.domain.*;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExceptionClaimMapper {
    record IdempotencyRecord(String requestHash, Long resourceId) { }

    boolean orderExists(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    Long findOrderStoreId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    boolean trackingEventExists(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                                @Param("trackingEventId") Long trackingEventId);
    boolean activeTenantUserExists(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    long countExceptions(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                         @Param("status") String status);
    List<ExceptionCase> findExceptions(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                                       @Param("status") String status, @Param("offset") int offset,
                                       @Param("limit") int limit);
    long countAccessibleExceptions(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                   @Param("orderId") Long orderId, @Param("status") String status, @Param("workbenchFilter") String workbenchFilter);
    default long countAccessibleExceptions(Long tenantId, Long userId, Long orderId, String status) {
        return countAccessibleExceptions(tenantId, userId, orderId, status, null);
    }
    List<ExceptionCase> findAccessibleExceptions(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                  @Param("orderId") Long orderId, @Param("status") String status, @Param("workbenchFilter") String workbenchFilter,
                                                  @Param("offset") int offset, @Param("limit") int limit);
    default List<ExceptionCase> findAccessibleExceptions(Long tenantId, Long userId, Long orderId, String status, int offset, int limit) {
        return findAccessibleExceptions(tenantId, userId, orderId, status, null, offset, limit);
    }
    ExceptionCase findException(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId);
    ExceptionCase findExceptionByNo(@Param("tenantId") Long tenantId, @Param("exceptionNo") String exceptionNo);
    int insertException(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                        @Param("exceptionNo") String exceptionNo, @Param("exceptionType") String exceptionType,
                        @Param("description") String description, @Param("reportedAt") LocalDateTime reportedAt);
    int transitionException(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                            @Param("version") Long version, @Param("responsibleParty") String responsibleParty);
    int assignException(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                        @Param("version") Long version, @Param("assignedToUserId") Long assignedToUserId,
                        @Param("responsibleParty") String responsibleParty);

    ClaimEligibility findClaimEligibility(@Param("tenantId") Long tenantId,
                                          @Param("exceptionId") Long exceptionId);
    ClaimRecord findClaim(@Param("tenantId") Long tenantId, @Param("claimId") Long claimId);
    ClaimRecord findClaimByException(@Param("tenantId") Long tenantId,
                                     @Param("exceptionId") Long exceptionId);
    ClaimRecord findClaimByNo(@Param("tenantId") Long tenantId, @Param("claimNo") String claimNo);
    int insertClaim(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                    @Param("claimNo") String claimNo, @Param("amount") BigDecimal amount,
                    @Param("currency") String currency);
    int transitionClaim(@Param("tenantId") Long tenantId, @Param("claimId") Long claimId,
                        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                        @Param("version") Long version, @Param("submittedAt") LocalDateTime submittedAt,
                        @Param("resolvedAt") LocalDateTime resolvedAt);

    List<HandlingRecord> findHandlingRecords(@Param("tenantId") Long tenantId,
                                             @Param("userId") Long userId,
                                             @Param("exceptionId") Long exceptionId);
    int insertHandlingRecord(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                             @Param("recordNo") String recordNo, @Param("handledByUserId") Long handledByUserId,
                             @Param("recordType") String recordType, @Param("content") String content,
                             @Param("createdAt") LocalDateTime createdAt);
    HandlingRecord findHandlingRecordByNo(@Param("tenantId") Long tenantId,
                                          @Param("exceptionId") Long exceptionId,
                                          @Param("recordNo") String recordNo);
    HandlingRecord findHandlingRecord(@Param("tenantId") Long tenantId, @Param("recordId") Long recordId);

    List<EvidenceAttachment> findEvidenceAttachments(@Param("tenantId") Long tenantId,
                                                     @Param("userId") Long userId,
                                                     @Param("exceptionId") Long exceptionId);
    EvidenceAttachment findEvidenceById(@Param("tenantId") Long tenantId, @Param("attachmentId") Long attachmentId);
    EvidenceAttachment findEvidenceByHash(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                                          @Param("contentSha256") String contentSha256);
    int insertEvidence(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                       @Param("uploadedByUserId") Long uploadedByUserId, @Param("originalFileName") String originalFileName,
                       @Param("contentType") String contentType, @Param("fileSize") Long fileSize,
                       @Param("contentSha256") String contentSha256, @Param("content") byte[] content, @Param("description") String description,
                       @Param("createdAt") LocalDateTime createdAt);
    default int insertEvidence(Long tenantId, Long exceptionId, Long uploadedByUserId, String originalFileName,
                               String contentType, Long fileSize, String contentSha256, byte[] content,
                               LocalDateTime createdAt) {
        return insertEvidence(tenantId, exceptionId, uploadedByUserId, originalFileName, contentType, fileSize,
                contentSha256, content, null, createdAt);
    }

    IdempotencyRecord findIdempotency(@Param("tenantId") Long tenantId,
                                      @Param("operationId") String operationId,
                                      @Param("idempotencyKey") String idempotencyKey);
    int insertIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                          @Param("idempotencyKey") String idempotencyKey,
                          @Param("requestPath") String requestPath, @Param("requestHash") String requestHash,
                          @Param("expiresAt") LocalDateTime expiresAt);
    int completeIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId);

    int insertAudit(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
                    @Param("actionType") String actionType, @Param("resourceType") String resourceType,
                    @Param("resourceId") Long resourceId, @Param("requestId") String requestId,
                    @Param("reason") String reason, @Param("detail") String detail,
                    @Param("occurredAt") LocalDateTime occurredAt);
}
