package com.shipflow.exceptioncase.api.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ClaimResponse(Long id, Long exceptionId, String claimNo, String status,
                            BigDecimal claimAmount, String currency, OffsetDateTime submittedAt,
                            OffsetDateTime resolvedAt, Long version, OffsetDateTime createdAt,
                            OffsetDateTime updatedAt) { }
