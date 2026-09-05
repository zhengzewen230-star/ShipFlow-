package com.shipflow.exceptioncase.domain;

import java.time.LocalDateTime;

public record EvidenceAttachment(Long id, Long tenantId, Long exceptionCaseId,
                                 Long uploadedByUserId, String originalFileName,
                                 String contentType, Long fileSize, String contentSha256,
                                 byte[] content, String description, LocalDateTime createdAt) {}
