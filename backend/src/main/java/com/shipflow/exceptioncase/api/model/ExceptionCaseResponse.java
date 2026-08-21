package com.shipflow.exceptioncase.api.model;
import java.time.OffsetDateTime;
public record ExceptionCaseResponse(Long id, Long orderId, Long storeId, String exceptionNo, String exceptionType,
                                    String status, String description, OffsetDateTime reportedAt,
                                    Long assignedToUserId, Long version, OffsetDateTime createdAt,
                                    OffsetDateTime updatedAt, String responsibleParty, ClaimResponse claim,
                                    String orderNo, String storeName, String assignedToUserName,
                                    java.util.List<HandlingRecordResponse> handlingRecords,
                                    java.util.List<EvidenceAttachmentResponse> evidenceAttachments,
                                    java.util.List<ExceptionTimelineEventResponse> timeline) {
    public ExceptionCaseResponse(Long id, Long orderId, Long storeId, String exceptionNo, String exceptionType,
                                 String status, String description, OffsetDateTime reportedAt,
                                 Long assignedToUserId, Long version, OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt, String responsibleParty, ClaimResponse claim) {
        this(id, orderId, storeId, exceptionNo, exceptionType, status, description, reportedAt,
                assignedToUserId, version, createdAt, updatedAt, responsibleParty, claim,
                null, null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
    public ExceptionCaseResponse(Long id, Long orderId, String exceptionNo, String exceptionType,
                                 String status, String description, OffsetDateTime reportedAt,
                                 Long assignedToUserId, Long version, OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt, ClaimResponse claim) {
        this(id, orderId, null, exceptionNo, exceptionType, status, description, reportedAt,
                assignedToUserId, version, createdAt, updatedAt, null, claim,
                null, null, null, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
}
