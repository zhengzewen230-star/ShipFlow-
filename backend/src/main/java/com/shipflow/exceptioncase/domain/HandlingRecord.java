package com.shipflow.exceptioncase.domain;

import java.time.LocalDateTime;

public record HandlingRecord(Long id, Long tenantId, Long exceptionCaseId, String recordNo,
                             Long handledByUserId, String recordType, String content,
                             LocalDateTime createdAt) { }
