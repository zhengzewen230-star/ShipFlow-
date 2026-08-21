package com.shipflow.exceptioncase.domain;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ClaimRecord(Long id, Long tenantId, Long exceptionCaseId, String claimNo,
                          String status, BigDecimal claimAmount, String currency,
                          LocalDateTime submittedAt, LocalDateTime resolvedAt, Long version,
                          LocalDateTime createdAt, LocalDateTime updatedAt, String claimReason,
                          BigDecimal resolvedAmount, String resultReason,
                          Long financeConfirmedByUserId, String financeConfirmedByUserName,
                          LocalDateTime financeConfirmedAt) {
    public ClaimRecord(Long id, Long tenantId, Long exceptionCaseId, String claimNo,
                       String status, BigDecimal claimAmount, String currency,
                       LocalDateTime submittedAt, LocalDateTime resolvedAt, Long version,
                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, tenantId, exceptionCaseId, claimNo, status, claimAmount, currency, submittedAt,
                resolvedAt, version, createdAt, updatedAt, null, null, null, null, null, null);
    }
}
