package com.shipflow.exceptioncase.api.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ClaimResponse(Long id, Long exceptionId, String claimNo, String status,
                            BigDecimal claimAmount, String currency, OffsetDateTime submittedAt,
                            OffsetDateTime resolvedAt, Long version, OffsetDateTime createdAt,
                            OffsetDateTime updatedAt, String claimReason, BigDecimal resolvedAmount,
                            String resultReason, Long financeConfirmedByUserId,
                            String financeConfirmedByUserName, OffsetDateTime financeConfirmedAt,
                            java.util.List<Long> evidenceAttachmentIds) {
    public ClaimResponse(Long id, Long exceptionId, String claimNo, String status,
                         BigDecimal claimAmount, String currency, OffsetDateTime submittedAt,
                         OffsetDateTime resolvedAt, Long version, OffsetDateTime createdAt,
                         OffsetDateTime updatedAt) {
        this(id, exceptionId, claimNo, status, claimAmount, currency, submittedAt, resolvedAt,
                version, createdAt, updatedAt, null, null, null, null, null, null, java.util.List.of());
    }
}
