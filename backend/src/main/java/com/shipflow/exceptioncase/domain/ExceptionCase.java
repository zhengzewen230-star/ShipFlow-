package com.shipflow.exceptioncase.domain;
import java.time.LocalDateTime;
public record ExceptionCase(Long id, Long tenantId, Long orderId, Long storeId, String exceptionNo,
                            String exceptionType, String status, String description,
                            LocalDateTime reportedAt, Long version, LocalDateTime createdAt,
                            LocalDateTime updatedAt, Long assignedToUserId, String responsibleParty,
                            String orderNo, String storeName, String assignedToUserName) {
    public ExceptionCase(Long id, Long tenantId, Long orderId, String exceptionNo, String exceptionType,
                         String status, String description, LocalDateTime reportedAt, Long version,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Long assignedToUserId) {
        this(id, tenantId, orderId, orderId, exceptionNo, exceptionType, status, description,
                reportedAt, version, createdAt, updatedAt, assignedToUserId, null, null, null, null);
    }

    public ExceptionCase(Long id, Long tenantId, Long orderId, Long storeId, String exceptionNo,
                         String exceptionType, String status, String description, LocalDateTime reportedAt,
                         Long version, LocalDateTime createdAt, LocalDateTime updatedAt,
                         Long assignedToUserId, String responsibleParty) {
        this(id, tenantId, orderId, storeId, exceptionNo, exceptionType, status, description, reportedAt,
                version, createdAt, updatedAt, assignedToUserId, responsibleParty, null, null, null);
    }
}
