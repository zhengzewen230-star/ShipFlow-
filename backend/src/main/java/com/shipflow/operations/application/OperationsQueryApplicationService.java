package com.shipflow.operations.application;

import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.mapper.OperationsMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationsQueryApplicationService {
    private final OperationsMapper mapper;
    public OperationsQueryApplicationService(OperationsMapper mapper) { this.mapper = mapper; }
    public OperationsSummary summary(Long tenantId) { return mapper.findSummary(tenantId); }
    public OperationsTodos todos(Long tenantId) { return mapper.findTodos(tenantId); }
}
