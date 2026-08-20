package com.shipflow.store.api.model;

import java.time.LocalDateTime;

/** 店铺最近审计摘要，不返回审计明细或敏感请求内容。 */
public record StoreAuditSummary(String actionType, String resultStatus, LocalDateTime occurredAt) { }
