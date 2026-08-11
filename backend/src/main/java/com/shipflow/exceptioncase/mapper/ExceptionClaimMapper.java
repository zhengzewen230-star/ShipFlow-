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
    boolean trackingEventExists(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                                @Param("trackingEventId") Long trackingEventId);
    boolean activeTenantUserExists(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    long countExceptions(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                         @Param("status") String status);
    List<ExceptionCase> findExceptions(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                                       @Param("status") String status, @Param("offset") int offset,
                                       @Param("limit") int limit);
    ExceptionCase findException(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId);
    ExceptionCase findExceptionByNo(@Param("tenantId") Long tenantId, @Param("exceptionNo") String exceptionNo);
    int insertException(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                        @Param("exceptionNo") String exceptionNo, @Param("exceptionType") String exceptionType,
                        @Param("description") String description, @Param("reportedAt") LocalDateTime reportedAt);
    int transitionException(@Param("tenantId") Long tenantId, @Param("exceptionId") Long exceptionId,
                            @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                            @Param("version") Long version);

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
