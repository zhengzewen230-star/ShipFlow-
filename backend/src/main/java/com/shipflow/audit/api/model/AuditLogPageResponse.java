package com.shipflow.audit.api.model;
import java.util.List;
public record AuditLogPageResponse(int page,int pageSize,long totalPages,long total,List<AuditLogResponse> items) { }
