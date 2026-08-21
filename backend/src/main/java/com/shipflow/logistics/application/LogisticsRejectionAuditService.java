package com.shipflow.logistics.application;

import com.shipflow.logistics.mapper.LogisticsAuditMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits security-relevant business rejections independently of the caller transaction. */
@Service
public class LogisticsRejectionAuditService {
    private final LogisticsAuditMapper mapper;

    public LogisticsRejectionAuditService(LogisticsAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tenantId, Long operatorUserId, String actionType, String resourceType,
                       Long resourceId, String requestId, String reason) {
        mapper.insertRejection(tenantId, operatorUserId, actionType, resourceType, resourceId,
                requestId, reason);
    }
}
