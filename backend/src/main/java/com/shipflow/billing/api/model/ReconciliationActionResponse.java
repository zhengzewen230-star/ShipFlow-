package com.shipflow.billing.api.model;

import java.time.OffsetDateTime;

public record ReconciliationActionResponse(Long id, String actionType, String statusBefore, String statusAfter,
                                           String remark, Long operatorUserId, String requestId,
                                           OffsetDateTime occurredAt) { }
