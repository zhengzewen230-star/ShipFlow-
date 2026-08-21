package com.shipflow.audit.api.model;
import java.time.OffsetDateTime; import java.util.Map;
public record AuditLogResponse(Long id, Long tenantId, Long operatorUserId, String actionType, String resourceType,
                               Long resourceId, String resultStatus, Map<String,Object> detail,
                               OffsetDateTime occurredAt, OffsetDateTime createdAt) { }
