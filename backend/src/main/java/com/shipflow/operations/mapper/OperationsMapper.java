package com.shipflow.operations.mapper;

import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationsMapper {
    OperationsSummary findSummary(@Param("tenantId") Long tenantId);
    OperationsTodos findTodos(@Param("tenantId") Long tenantId);
}
