package com.shipflow.exceptioncase.api.model;

import java.time.OffsetDateTime;

public record ExceptionTimelineEventResponse(String eventType, String title, String description,
                                             Long operatorUserId, String operatorName, String source,
                                             OffsetDateTime occurredAt, String statusBefore,
                                             String statusAfter, Long relatedId, String requestId) { }
