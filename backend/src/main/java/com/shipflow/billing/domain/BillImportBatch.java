package com.shipflow.billing.domain;

import java.time.LocalDateTime;

public record BillImportBatch(Long id, Long tenantId, Long providerId, String batchNo, String fileName,
                              String fileHash, long fileSize, int totalCount, int successCount, int failureCount,
                              int duplicateCount,
                              String status, LocalDateTime importedAt, Long version, LocalDateTime createdAt,
                              LocalDateTime updatedAt) { }
