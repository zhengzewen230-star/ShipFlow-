package com.shipflow.exceptioncase.api.model;

import java.time.OffsetDateTime;

public record EvidenceAttachmentResponse(Long id, Long exceptionId, Long uploadedByUserId,
                                         String originalFileName, String contentType,
                                         Long fileSize, String contentSha256,
                                         String description, OffsetDateTime createdAt) {
    public EvidenceAttachmentResponse(Long id, Long exceptionId, Long uploadedByUserId,
                                      String originalFileName, String contentType, Long fileSize,
                                      String contentSha256, OffsetDateTime createdAt) {
        this(id, exceptionId, uploadedByUserId, originalFileName, contentType, fileSize, contentSha256, null, createdAt);
    }
}
