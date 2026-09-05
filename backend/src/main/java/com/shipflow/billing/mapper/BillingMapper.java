package com.shipflow.billing.mapper;

import com.shipflow.billing.domain.*;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BillingMapper {
    record IdempotencyRecord(String requestHash, Long resourceId, String processingStatus) { }

    boolean providerExists(@Param("providerId") Long providerId);
    IdempotencyRecord findIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                                      @Param("idempotencyKey") String idempotencyKey);
    int insertIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                          @Param("idempotencyKey") String idempotencyKey, @Param("requestPath") String requestPath,
                          @Param("requestHash") String requestHash, @Param("expiresAt") LocalDateTime expiresAt);
    int completeIdempotency(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                            @Param("idempotencyKey") String idempotencyKey, @Param("resourceType") String resourceType,
                            @Param("resourceId") Long resourceId, @Param("responseStatus") int responseStatus);
    BillImportBatch findBatchByHash(@Param("tenantId") Long tenantId, @Param("providerId") Long providerId, @Param("fileHash") String fileHash);
    BillImportBatch findBatch(@Param("tenantId") Long tenantId, @Param("batchId") Long batchId);
    boolean hasBatchAccess(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("batchId") Long batchId);
    long countBatches(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("providerId") Long providerId, @Param("status") String status);
    List<BillImportBatch> findBatches(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("providerId") Long providerId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    int insertBatch(@Param("tenantId") Long tenantId, @Param("providerId") Long providerId, @Param("batchNo") String batchNo, @Param("fileName") String fileName, @Param("fileHash") String fileHash, @Param("fileSize") long fileSize, @Param("importedAt") LocalDateTime importedAt);
    int finishBatch(@Param("tenantId") Long tenantId, @Param("batchId") Long batchId, @Param("total") int total, @Param("success") int success, @Param("failure") int failure, @Param("duplicate") int duplicate, @Param("status") String status, @Param("version") Long version);
    long countDetails(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("batchId") Long batchId, @Param("status") String status);
    List<BillDetail> findDetails(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("batchId") Long batchId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    BillDetail findDetailByProviderNo(@Param("tenantId") Long tenantId, @Param("providerId") Long providerId, @Param("providerBillDetailNo") String providerBillDetailNo);
    OrderFeeMatch findOrderByTracking(@Param("tenantId") Long tenantId, @Param("providerId") Long providerId, @Param("trackingNo") String trackingNo);
    int insertDetail(@Param("tenantId") Long tenantId, @Param("batchId") Long batchId, @Param("providerId") Long providerId, @Param("providerBillDetailNo") String providerBillDetailNo, @Param("lineNo") int lineNo, @Param("orderId") Long orderId, @Param("trackingNo") String trackingNo, @Param("billedAmount") BigDecimal billedAmount, @Param("currency") String currency, @Param("feeType") String feeType, @Param("detailStatus") String detailStatus, @Param("errorMessage") String errorMessage, @Param("rawLineMasked") String rawLineMasked, @Param("errorHandlingStatus") String errorHandlingStatus);
    BillDetail findDetailByBatchLine(@Param("tenantId") Long tenantId, @Param("batchId") Long batchId, @Param("lineNo") int lineNo);
    int insertReconciliation(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("billDetailId") Long billDetailId, @Param("systemAmount") BigDecimal systemAmount, @Param("billedAmount") BigDecimal billedAmount, @Param("difference") BigDecimal difference, @Param("status") String status);
    ReconciliationRecord findReconciliation(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId);
    boolean hasReconciliationAccess(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("reconciliationId") Long reconciliationId);
    ReconciliationRecord findReconciliationByDetail(@Param("tenantId") Long tenantId, @Param("billDetailId") Long billDetailId);
    long countReconciliations(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("orderId") Long orderId, @Param("status") String status);
    List<ReconciliationRecord> findReconciliations(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("orderId") Long orderId, @Param("status") String status, @Param("offset") int offset, @Param("limit") int limit);
    int confirmReconciliation(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId, @Param("confirmedBy") Long confirmedBy, @Param("resolutionType") String resolutionType, @Param("remark") String remark, @Param("confirmedAt") LocalDateTime confirmedAt, @Param("version") Long version);
    int rejectReconciliation(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId, @Param("operatorId") Long operatorId, @Param("remark") String remark, @Param("occurredAt") LocalDateTime occurredAt, @Param("version") Long version);
    int commentReconciliation(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId, @Param("remark") String remark, @Param("version") Long version);
    int insertReconciliationHistory(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId, @Param("actionType") String actionType, @Param("statusBefore") String statusBefore, @Param("statusAfter") String statusAfter, @Param("remark") String remark, @Param("operatorId") Long operatorId, @Param("requestId") String requestId, @Param("occurredAt") LocalDateTime occurredAt);
    List<ReconciliationActionHistory> findReconciliationHistory(@Param("tenantId") Long tenantId, @Param("reconciliationId") Long reconciliationId);
    int insertAudit(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId, @Param("actionType") String actionType, @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId, @Param("requestId") String requestId, @Param("reason") String reason, @Param("detail") String detail, @Param("occurredAt") LocalDateTime occurredAt);
}
