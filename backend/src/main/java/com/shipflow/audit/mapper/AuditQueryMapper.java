package com.shipflow.audit.mapper;
import com.shipflow.audit.domain.AuditLog; import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface AuditQueryMapper {
 boolean tenantExists(@Param("tenantId") Long tenantId);
 long count(@Param("tenantId") Long tenantId,@Param("resourceType") String resourceType,@Param("resourceId") Long resourceId,@Param("action") String action,@Param("operatorId") Long operatorId,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to);
 List<AuditLog> findPage(@Param("tenantId") Long tenantId,@Param("resourceType") String resourceType,@Param("resourceId") Long resourceId,@Param("action") String action,@Param("operatorId") Long operatorId,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to,@Param("offset") int offset,@Param("limit") int limit);
 AuditLog findById(@Param("tenantId") Long tenantId,@Param("auditId") Long auditId);
}
