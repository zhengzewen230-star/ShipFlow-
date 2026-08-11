package com.shipflow.tracking.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrackingCallbackRequest(
        @NotEmpty @Size(max = 100) List<@Valid TrackingEventInput> events) {
}
