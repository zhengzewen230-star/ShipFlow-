package com.shipflow.exceptioncase.api.model;

import java.time.OffsetDateTime;

public record HandlingRecordResponse(Long id, Long exceptionId, String recordNo,
                                     Long handledByUserId, String recordType,
                                     String content, OffsetDateTime createdAt) { }
