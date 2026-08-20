package com.shipflow.exceptioncase.domain;
import java.time.LocalDateTime;
public record ExceptionCase(Long id, Long tenantId, Long orderId, Long storeId, String exceptionNo,
                            String exceptionType, String status, String description,
                            LocalDateTime reportedAt, Long version, LocalDateTime createdAt,
                            LocalDateTime updatedAt, Long assignedToUserId, String responsibleParty) {
    public ExceptionCase(Long id, Long tenantId, Long orderId, String exceptionNo, String exceptionType,
                         String status, String description, LocalDateTime reportedAt, Long version,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Long assignedToUserId) {
        this(id, tenantId, orderId, orderId, exceptionNo, exceptionType, status, description,
                reportedAt, version, createdAt, updatedAt, assignedToUserId, null);
    }
}
