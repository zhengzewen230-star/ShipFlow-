package com.shipflow.exceptioncase.api.model;
import java.time.OffsetDateTime;
public record ExceptionCaseResponse(Long id, Long orderId, Long storeId, String exceptionNo, String exceptionType,
                                    String status, String description, OffsetDateTime reportedAt,
                                    Long assignedToUserId, Long version, OffsetDateTime createdAt,
                                    OffsetDateTime updatedAt, String responsibleParty, ClaimResponse claim) {
    public ExceptionCaseResponse(Long id, Long orderId, String exceptionNo, String exceptionType,
                                 String status, String description, OffsetDateTime reportedAt,
                                 Long assignedToUserId, Long version, OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt, ClaimResponse claim) {
        this(id, orderId, null, exceptionNo, exceptionType, status, description, reportedAt,
                assignedToUserId, version, createdAt, updatedAt, null, claim);
    }
}
