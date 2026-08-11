package com.shipflow.tracking.api.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record TrackingEventInput(
        @NotBlank @Size(max = 128) String trackingNo,
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank @Size(max = 64) String eventCode,
        @Size(max = 255) String eventDescription,
        @NotNull OffsetDateTime eventTime,
        @NotNull JsonNode rawPayload) {
}
