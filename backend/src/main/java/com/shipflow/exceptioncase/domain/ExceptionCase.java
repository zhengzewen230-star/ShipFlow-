package com.shipflow.exceptioncase.domain;
import java.time.LocalDateTime;
public record ExceptionCase(Long id, Long tenantId, Long orderId, String exceptionNo,
                            String exceptionType, String status, String description,
                            LocalDateTime reportedAt, Long version, LocalDateTime createdAt,
                            LocalDateTime updatedAt, Long assignedToUserId) { }
