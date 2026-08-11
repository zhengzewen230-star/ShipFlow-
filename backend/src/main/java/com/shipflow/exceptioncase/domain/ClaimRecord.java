package com.shipflow.exceptioncase.domain;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ClaimRecord(Long id, Long tenantId, Long exceptionCaseId, String claimNo,
                          String status, BigDecimal claimAmount, String currency,
                          LocalDateTime submittedAt, LocalDateTime resolvedAt, Long version,
                          LocalDateTime createdAt, LocalDateTime updatedAt) { }
