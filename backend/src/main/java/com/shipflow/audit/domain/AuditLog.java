package com.shipflow.audit.domain;
import java.time.LocalDateTime;
public record AuditLog(Long id, Long tenantId, Long operatorUserId, String actionType, String resourceType,
                       Long resourceId, String resultStatus, String detail, LocalDateTime occurredAt,
                       LocalDateTime createdAt) { }
