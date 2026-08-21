package com.shipflow.exceptioncase.domain;

import java.time.LocalDateTime;

public record ExceptionTimelineEvent(Long id, String eventType, String title, String description,
                                     Long operatorUserId, String operatorName, String source,
                                     LocalDateTime occurredAt, String statusBefore, String statusAfter,
                                     Long relatedId, String requestId) { }
