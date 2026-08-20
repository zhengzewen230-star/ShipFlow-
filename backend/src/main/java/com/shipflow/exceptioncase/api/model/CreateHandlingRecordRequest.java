package com.shipflow.exceptioncase.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateHandlingRecordRequest(
        @NotBlank @Pattern(regexp = "CONTACT|FOLLOW_UP|PROVIDER_FEEDBACK|INTERNAL_NOTE|OTHER") String recordType,
        @NotBlank @Size(max = 4000) String content) { }
