package com.shipflow.billing.api.model;

import java.time.OffsetDateTime;

public record BillBatchResponse(Long id, Long providerId, String batchNo, String fileName, String fileHash,
                                long fileSize, int totalCount, int successCount, int failureCount, int duplicateCount, String status,
                                OffsetDateTime importedAt, Long version, OffsetDateTime createdAt,
                                OffsetDateTime updatedAt) { }
