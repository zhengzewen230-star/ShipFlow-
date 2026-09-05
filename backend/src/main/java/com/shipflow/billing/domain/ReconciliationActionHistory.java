package com.shipflow.billing.domain;

import java.time.LocalDateTime;

public record ReconciliationActionHistory(Long id, Long reconciliationRecordId, String actionType,
                                           String statusBefore, String statusAfter, String remark,
                                           Long operatorUserId, String requestId, LocalDateTime occurredAt) { }
