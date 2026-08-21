package com.shipflow.exceptioncase.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateHandlingRecordRequest(
        @NotBlank @Pattern(regexp = "CONTACT|FOLLOW_UP|PROVIDER_FEEDBACK|INTERNAL_NOTE|OTHER") String recordType,
        @NotBlank @Size(max = 4000) String content,
        @NotNull @PositiveOrZero Long version) {
    public CreateHandlingRecordRequest(String recordType, String content) { this(recordType, content, 0L); }
}
